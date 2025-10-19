package com.jingwook.mafia_server.services;

import com.github.f4b6a3.uuid.UuidCreator;
import com.jingwook.mafia_server.domains.Game;
import com.jingwook.mafia_server.dtos.*;
import com.jingwook.mafia_server.entities.GameActionEntity;
import com.jingwook.mafia_server.entities.GameEntity;
import com.jingwook.mafia_server.entities.GamePlayerEntity;
import com.jingwook.mafia_server.entities.RoomMemberEntity;
import com.jingwook.mafia_server.enums.ActionType;
import com.jingwook.mafia_server.enums.DeathReason;
import com.jingwook.mafia_server.enums.GamePhase;
import com.jingwook.mafia_server.enums.PlayerRole;
import com.jingwook.mafia_server.enums.Team;
import com.jingwook.mafia_server.events.GameEndedEvent;
import com.jingwook.mafia_server.events.GameStartedEvent;
import com.jingwook.mafia_server.events.PhaseChangedEvent;
import com.jingwook.mafia_server.events.PlayerDiedEvent;
import com.jingwook.mafia_server.repositories.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GameService {
    private final GameR2dbcRepository gameRepository;
    private final GamePlayerR2dbcRepository gamePlayerRepository;
    private final GameActionR2dbcRepository gameActionRepository;
    private final RoomMemberR2dbcRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 페이즈별 제한 시간 (초)
    private static final int NIGHT_DURATION = 30;
    private static final int DAY_DURATION = 30;
    private static final int VOTE_DURATION = 10;
    private static final int DEFENSE_DURATION = 10;
    private static final int RESULT_DURATION = 10;

    public GameService(
            GameR2dbcRepository gameRepository,
            GamePlayerR2dbcRepository gamePlayerRepository,
            GameActionR2dbcRepository gameActionRepository,
            RoomMemberR2dbcRepository roomMemberRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.gameRepository = gameRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.gameActionRepository = gameActionRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Mono<GameStateResponse> startGame(String roomId) {
        return checkNoActiveGame(roomId)
                .then(createNewGame(roomId))
                .flatMap(game -> initializeGamePlayers(game, roomId))
                .map(this::buildGameStateResponse)
                .doOnSuccess(gameState -> {
                    // 게임 시작 이벤트 발행
                    eventPublisher.publishEvent(new GameStartedEvent(roomId, gameState.getGameId()));
                });
    }

    private Mono<Void> checkNoActiveGame(String roomId) {
        return gameRepository.findActiveGameByRoomId(roomId)
                .flatMap(existingGame -> Mono.<Void>error(
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "게임이 이미 진행 중입니다")))
                .then();
    }

    private Mono<GameEntity> createNewGame(String roomId) {
        String gameId = UuidCreator.getTimeOrderedEpoch().toString();
        LocalDateTime now = LocalDateTime.now();
        GameEntity game = GameEntity.builder()
                .id(gameId)
                .roomId(roomId)
                .currentPhase(GamePhase.NIGHT.toString())
                .dayCount(1)
                .phaseStartTime(now)
                .phaseDurationSeconds(NIGHT_DURATION)
                .startedAt(now)
                .build();
        return gameRepository.save(game);
    }

    private Mono<GameEntity> initializeGamePlayers(GameEntity game, String roomId) {
        return roomMemberRepository.findByRoomId(roomId)
                .collectList()
                .flatMap(members -> assignRolesAndSavePlayers(game.getId(), members))
                .thenReturn(game);
    }

    private GameStateResponse buildGameStateResponse(GameEntity game) {
        Game domain = game.toDomain();
        return GameStateResponse.builder()
                .gameId(game.getId())
                .currentPhase(game.getCurrentPhaseAsEnum())
                .dayCount(game.getDayCount())
                .phaseStartTime(game.getPhaseStartTime())
                .phaseDurationSeconds(game.getPhaseDurationSeconds())
                .remainingSeconds(domain.calculateRemainingSeconds())
                .winnerTeam(game.getWinnerTeam() != null ? Team.valueOf(game.getWinnerTeam()) : null)
                .finishedAt(game.getFinishedAt())
                .build();
    }

    private Mono<Void> assignRolesAndSavePlayers(String gameId, List<RoomMemberEntity> members) {
        return validatePlayerCount(members)
                .then(Mono.defer(() -> {
                    // 도메인 로직으로 역할 생성 및 셔플
                    List<PlayerRole> shuffledRoles = Game.createShuffledRoles();
                    List<GamePlayerEntity> players = createGamePlayers(gameId, members, shuffledRoles);
                    return gamePlayerRepository.saveAll(players).then();
                }));
    }

    private Mono<Void> validatePlayerCount(List<RoomMemberEntity> members) {
        if (members.size() != 8) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "게임은 정확히 8명이 필요합니다. 현재: " + members.size() + "명"));
        }
        return Mono.empty();
    }

    private List<GamePlayerEntity> createGamePlayers(String gameId, List<RoomMemberEntity> members,
            List<PlayerRole> roles) {
        List<GamePlayerEntity> players = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            players.add(createGamePlayer(gameId, members.get(i), roles.get(i), i + 1));
        }
        return players;
    }

    private GamePlayerEntity createGamePlayer(String gameId, RoomMemberEntity member, PlayerRole role, int position) {
        return GamePlayerEntity.builder()
                .id(UuidCreator.getTimeOrderedEpoch().toString())
                .gameId(gameId)
                .userId(member.getUserId())
                .role(role.toString())
                .isAlive(true)
                .position(position)
                .build();
    }

    public Mono<GameStateResponse> getGameState(String gameId) {
        return gameRepository.findById(gameId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "게임을 찾을 수 없습니다")))
                .map(this::buildGameStateResponse);
    }

    public Mono<MyRoleResponse> getMyRole(String gameId, String userId) {
        return gamePlayerRepository.findByGameIdAndUserId(gameId, userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "게임 참가자를 찾을 수 없습니다")))
                .map(player -> MyRoleResponse.builder()
                        .role(player.getRoleAsEnum())
                        .isAlive(player.getIsAlive())
                        .position(player.getPosition())
                        .build());
    }

    public Mono<PoliceCheckResultResponse> getPoliceCheckResults(String gameId, String userId) {
        return gamePlayerRepository.findByGameIdAndUserId(gameId, userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "게임 참가자를 찾을 수 없습니다")))
                .flatMap(player -> {
                    // 경찰만 조회 가능
                    if (player.getRoleAsEnum() != PlayerRole.POLICE) {
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "경찰만 조회할 수 있습니다"));
                    }

                    return gameActionRepository.findByGameIdAndActorUserIdAndType(
                            gameId, userId, ActionType.POLICE_CHECK.toString())
                            .flatMap(action ->
                                gamePlayerRepository.findByGameIdAndUserId(gameId, action.getTargetUserId())
                                    .flatMap(targetPlayer ->
                                        userRepository.findById(targetPlayer.getUserId())
                                            .map(user -> PoliceCheckResultResponse.CheckResult.builder()
                                                    .targetUserId(targetPlayer.getUserId())
                                                    .targetUsername(user.getNickname())
                                                    .targetRole(targetPlayer.getRoleAsEnum())
                                                    .dayCount(action.getDayCount())
                                                    .build())
                                    )
                            )
                            .collectList()
                            .map(results -> PoliceCheckResultResponse.builder()
                                    .results(results)
                                    .build());
                });
    }

    public Mono<GamePlayersResponse> getPlayers(String gameId) {
        return gamePlayerRepository.findByGameId(gameId)
                .flatMap(player -> userRepository.findById(player.getUserId())
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "유저를 찾을 수 없습니다: " + player.getUserId())))
                        .map(user -> GamePlayerResponse.builder()
                                .userId(player.getUserId())
                                .username(user.getNickname())
                                .position(player.getPosition())
                                .isAlive(player.getIsAlive())
                                .diedAt(player.getDiedAt())
                                .build()))
                .collectList()
                .map(players -> GamePlayersResponse.builder()
                        .players(players)
                        .build());
    }

    @Transactional
    public Mono<Void> registerAction(String gameId, RegisterActionDto dto) {
        return gameRepository.findById(gameId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "게임을 찾을 수 없습니다")))
                .flatMap(game -> validateGameNotFinished(game)
                        .then(findAndValidatePlayer(gameId, dto.getActorUserId()))
                        .flatMap(player -> validateAndSaveAction(game, player, dto)))
                .then();
    }

    private Mono<Void> validateGameNotFinished(GameEntity game) {
        if (game.isFinished()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "종료된 게임입니다"));
        }
        return Mono.empty();
    }

    private Mono<GamePlayerEntity> findAndValidatePlayer(String gameId, String userId) {
        return gamePlayerRepository.findByGameIdAndUserId(gameId, userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "게임 참가자가 아닙니다")));
    }

    private Mono<Void> validateAndSaveAction(GameEntity game, GamePlayerEntity playerEntity, RegisterActionDto dto) {
        // 도메인 로직으로 권한 검증
        if (!playerEntity.toDomain().canPerformAction(game.getCurrentPhaseAsEnum(), dto.getType())) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 행동을 할 수 없습니다"));
        }
        return replaceAction(game, dto);
    }

    private Mono<Void> replaceAction(GameEntity game, RegisterActionDto dto) {
        return gameActionRepository.deleteByGameIdAndActorUserIdAndDayCountAndType(
                game.getId(), dto.getActorUserId(), game.getDayCount(), dto.getType().toString())
                .then(Mono.defer(() -> createAndSaveAction(game, dto)));
    }

    private Mono<Void> createAndSaveAction(GameEntity game, RegisterActionDto dto) {
        String actionId = UuidCreator.getTimeOrderedEpoch().toString();
        GameActionEntity action = GameActionEntity.builder()
                .id(actionId)
                .gameId(game.getId())
                .dayCount(game.getDayCount())
                .phase(game.getCurrentPhase())
                .type(dto.getType().toString())
                .actorUserId(dto.getActorUserId())
                .targetUserId(dto.getTargetUserId())
                .createdAt(LocalDateTime.now())
                .build();
        return gameActionRepository.save(action).then();
    }

    public Mono<VoteStatusResponse> getVoteStatus(String gameId, Integer dayCount) {
        return gameActionRepository.findByGameIdAndDayCountAndType(
                gameId, dayCount, ActionType.VOTE.toString())
                .collectList()
                .map(votes -> {
                    List<VoteStatusResponse.VoteInfo> voteInfos = votes.stream()
                            .map(vote -> VoteStatusResponse.VoteInfo.builder()
                                    .voterUserId(vote.getActorUserId())
                                    .targetUserId(vote.getTargetUserId())
                                    .build())
                            .toList();

                    Map<String, Long> voteCount = votes.stream()
                            .collect(Collectors.groupingBy(
                                    GameActionEntity::getTargetUserId,
                                    Collectors.counting()));

                    return VoteStatusResponse.builder()
                            .votes(voteInfos)
                            .voteCount(voteCount)
                            .build();
                });
    }

    @Transactional
    public Mono<NextPhaseResponse> nextPhase(String gameId) {
        return gameRepository.findById(gameId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "게임을 찾을 수 없습니다")))
                .flatMap(game -> processPhaseResult(game)
                        .flatMap(result -> handlePhaseTransition(game, result)));
    }

    private Mono<NextPhaseResponse> handlePhaseTransition(GameEntity game, NextPhaseResponse.PhaseResult result) {
        return checkGameEnd(game.getId())
                .flatMap(winnerOpt -> {
                    if (winnerOpt.isPresent()) {
                        return endGame(game, winnerOpt.get(), result);
                    } else {
                        return moveToNextPhase(game, result);
                    }
                });
    }

    private Mono<NextPhaseResponse> endGame(GameEntity gameEntity, Team winner,
            NextPhaseResponse.PhaseResult result) {
        // 도메인 로직 실행
        Game endedGame = gameEntity.toDomain().endGame(winner);

        // 엔티티 업데이트
        gameEntity.updateFromDomain(endedGame);
        gameEntity.markAsNotNew();

        NextPhaseResponse response = buildNextPhaseResponse(gameEntity, result);

        return gameRepository.save(gameEntity)
                .doOnSuccess(savedGame -> {
                    // 게임 종료 이벤트 발행
                    eventPublisher.publishEvent(new GameEndedEvent(
                            savedGame.getRoomId(),
                            savedGame.getId(),
                            winner));
                })
                .thenReturn(response);
    }

    private Mono<NextPhaseResponse> moveToNextPhase(GameEntity gameEntity, NextPhaseResponse.PhaseResult result) {
        // VOTE 페이즈에서만 처형 대상자 확인
        GamePhase currentPhase = gameEntity.getCurrentPhaseAsEnum();
        boolean hasExecutedTarget = (currentPhase == GamePhase.VOTE)
                && result.getExecutedUserId() != null
                && !result.getExecutedUserId().isEmpty();

        // 도메인 로직 실행
        Game nextPhaseGame = gameEntity.toDomain()
                .transitionToNextPhase(getPhaseDurationMap(), hasExecutedTarget);

        // 엔티티 업데이트
        gameEntity.updateFromDomain(nextPhaseGame);
        gameEntity.markAsNotNew();

        NextPhaseResponse response = buildNextPhaseResponse(gameEntity, result);

        return gameRepository.save(gameEntity)
                .doOnSuccess(savedGame -> {
                    // 페이즈 변경 이벤트 발행
                    eventPublisher.publishEvent(new PhaseChangedEvent(
                            savedGame.getRoomId(),
                            savedGame.getId(),
                            response));
                })
                .thenReturn(response);
    }

    private Mono<NextPhaseResponse.PhaseResult> processPhaseResult(GameEntity game) {
        GamePhase currentPhase = game.getCurrentPhaseAsEnum();

        return switch (currentPhase) {
            case NIGHT -> processNightPhase(game);
            case DAY -> Mono.just(NextPhaseResponse.PhaseResult.builder().build());
            case VOTE -> processVotePhase(game);
            case DEFENSE -> Mono.just(NextPhaseResponse.PhaseResult.builder().build());
            case RESULT -> processResultPhase(game);
        };
    }

    private Mono<NextPhaseResponse.PhaseResult> processNightPhase(GameEntity game) {
        Mono<String> mafiaTargetMono = getMafiaKillTarget(game);
        Mono<String> doctorTargetMono = getDoctorHealTarget(game);

        return Mono.zip(mafiaTargetMono, doctorTargetMono)
                .flatMap(tuple -> executeNightKill(game, tuple.getT1(), tuple.getT2()));
    }

    private Mono<String> getMafiaKillTarget(GameEntity game) {
        return gameActionRepository.findByGameIdAndDayCountAndType(
                game.getId(), game.getDayCount(), ActionType.MAFIA_KILL.toString())
                .map(GameActionEntity::getTargetUserId)
                .collectList()
                .map(targetUserIds -> Game.selectTargetFromVotes(targetUserIds, true))
                .defaultIfEmpty("");
    }

    private Mono<String> getDoctorHealTarget(GameEntity game) {
        return gameActionRepository.findByGameIdAndDayCountAndType(
                game.getId(), game.getDayCount(), ActionType.DOCTOR_HEAL.toString())
                .next()
                .map(GameActionEntity::getTargetUserId)
                .defaultIfEmpty("");
    }

    private Mono<NextPhaseResponse.PhaseResult> executeNightKill(GameEntity game, String mafiaTarget,
            String doctorTarget) {
        List<String> deaths = new ArrayList<>();

        // 도메인 로직: 의사가 마피아 타겟을 살렸는지 판단
        if (Game.isSavedByDoctor(mafiaTarget, doctorTarget)) {
            return Mono.just(NextPhaseResponse.PhaseResult.builder()
                    .deaths(deaths)
                    .build());
        }

        // 마피아 타겟 사망 처리
        deaths.add(mafiaTarget);
        return killPlayer(game.getId(), mafiaTarget)
                .thenReturn(NextPhaseResponse.PhaseResult.builder()
                        .deaths(deaths)
                        .build());
    }

    private Mono<NextPhaseResponse.PhaseResult> processVotePhase(GameEntity game) {
        return getExecutedUserIdFromVotes(game)
                .map(executedUserId -> NextPhaseResponse.PhaseResult.builder()
                        .executedUserId(executedUserId)
                        .build());
    }

    private Mono<NextPhaseResponse.PhaseResult> processResultPhase(GameEntity game) {
        return getExecutedUserIdFromVotes(game)
                .flatMap(executedUserId -> {
                    if (executedUserId == null || executedUserId.isEmpty()) {
                        return Mono.just(NextPhaseResponse.PhaseResult.builder().build());
                    }

                    return killPlayer(game.getId(), executedUserId)
                            .thenReturn(NextPhaseResponse.PhaseResult.builder()
                                    .executedUserId(executedUserId)
                                    .build());
                });
    }

    private Mono<String> getExecutedUserIdFromVotes(GameEntity game) {
        return Mono.zip(
                gameActionRepository.findByGameIdAndDayCountAndType(
                        game.getId(), game.getDayCount(), ActionType.VOTE.toString())
                        .map(GameActionEntity::getTargetUserId)
                        .collectList(),
                gamePlayerRepository.countByGameIdAndIsAlive(game.getId(), true)).map(tuple -> {
                    List<String> targetUserIds = tuple.getT1();
                    long alivePlayers = tuple.getT2();
                    return Game.selectTargetFromVotesWithMajority(targetUserIds, alivePlayers);
                }).defaultIfEmpty("");
    }

    private Mono<Void> killPlayer(String gameId, String userId) {
        return Mono.zip(
                gamePlayerRepository.findByGameIdAndUserId(gameId, userId),
                gameRepository.findById(gameId)).flatMap(tuple -> {
                    GamePlayerEntity player = tuple.getT1();
                    GameEntity game = tuple.getT2();

                    player.setIsAlive(false);
                    player.setDiedAt(LocalDateTime.now());
                    player.markAsNotNew();

                    return gamePlayerRepository.save(player)
                            .doOnSuccess(savedPlayer -> {
                                // 플레이어 사망 이벤트 발행
                                eventPublisher.publishEvent(new PlayerDiedEvent(
                                        game.getRoomId(),
                                        gameId,
                                        List.of(userId),
                                        DeathReason.KILLED));
                            });
                }).then();
    }

    private Mono<Optional<Team>> checkGameEnd(String gameId) {
        Mono<Long> aliveMafiaMono = gamePlayerRepository.countByGameIdAndIsAliveAndRole(
                gameId, true, PlayerRole.MAFIA.toString());

        // 전체 생존자 - 마피아 = 시민팀 (시민 + 의사 + 경찰)
        Mono<Long> aliveCitizensTeamMono = gamePlayerRepository.countByGameIdAndIsAlive(gameId, true)
                .zipWith(aliveMafiaMono)
                .map(tuple -> tuple.getT1() - tuple.getT2());

        return Mono.zip(aliveMafiaMono, aliveCitizensTeamMono)
                .map(tuple -> Game.determineWinner(tuple.getT1(), tuple.getT2()));
    }

    private NextPhaseResponse buildNextPhaseResponse(GameEntity game, NextPhaseResponse.PhaseResult result) {
        result.setWinnerTeam(game.getWinnerTeam() != null ? Team.valueOf(game.getWinnerTeam()) : null);

        return NextPhaseResponse.builder()
                .currentPhase(game.getCurrentPhaseAsEnum())
                .dayCount(game.getDayCount())
                .phaseStartTime(game.getPhaseStartTime())
                .phaseDurationSeconds(game.getPhaseDurationSeconds())
                .lastPhaseResult(result)
                .build();
    }

    /**
     * 페이즈별 지속 시간 맵 생성
     */
    private Map<GamePhase, Integer> getPhaseDurationMap() {
        return Map.of(
                GamePhase.NIGHT, NIGHT_DURATION,
                GamePhase.DAY, DAY_DURATION,
                GamePhase.VOTE, VOTE_DURATION,
                GamePhase.DEFENSE, DEFENSE_DURATION,
                GamePhase.RESULT, RESULT_DURATION);
    }
}
