package com.jingwook.mafia_server.services;

import com.github.f4b6a3.uuid.UuidCreator;
import com.jingwook.mafia_server.dtos.*;
import com.jingwook.mafia_server.entities.GameActionEntity;
import com.jingwook.mafia_server.entities.GameEntity;
import com.jingwook.mafia_server.entities.GamePlayerEntity;
import com.jingwook.mafia_server.entities.RoomMemberEntity;
import com.jingwook.mafia_server.enums.ActionType;
import com.jingwook.mafia_server.enums.GamePhase;
import com.jingwook.mafia_server.enums.PlayerRole;
import com.jingwook.mafia_server.events.GameStartedEvent;
import com.jingwook.mafia_server.repositories.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
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
    private static final int NIGHT_DURATION = 60;
    private static final int DAY_DURATION = 180;
    private static final int VOTE_DURATION = 120;
    private static final int DEFENSE_DURATION = 60;
    private static final int RESULT_DURATION = 30;

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
        return GameStateResponse.builder()
                .gameId(game.getId())
                .currentPhase(game.getCurrentPhase())
                .dayCount(game.getDayCount())
                .phaseStartTime(game.getPhaseStartTime())
                .phaseDurationSeconds(game.getPhaseDurationSeconds())
                .remainingSeconds(calculateRemainingSeconds(game))
                .winnerTeam(game.getWinnerTeam())
                .finishedAt(game.getFinishedAt())
                .build();
    }

    private Mono<Void> assignRolesAndSavePlayers(String gameId, List<RoomMemberEntity> members) {
        return validatePlayerCount(members)
                .then(Mono.defer(() -> {
                    List<PlayerRole> shuffledRoles = createAndShuffleRoles();
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

    private List<PlayerRole> createAndShuffleRoles() {
        List<PlayerRole> roles = Arrays.asList(
                PlayerRole.MAFIA, PlayerRole.MAFIA,
                PlayerRole.DOCTOR,
                PlayerRole.POLICE,
                PlayerRole.CITIZEN, PlayerRole.CITIZEN, PlayerRole.CITIZEN, PlayerRole.CITIZEN
        );
        Collections.shuffle(roles);
        return roles;
    }

    private List<GamePlayerEntity> createGamePlayers(String gameId, List<RoomMemberEntity> members, List<PlayerRole> roles) {
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
                        .role(player.getRole())
                        .isAlive(player.getIsAlive())
                        .position(player.getPosition())
                        .build());
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

    private Mono<Void> validateAndSaveAction(GameEntity game, GamePlayerEntity player, RegisterActionDto dto) {
        return parseActionType(dto.getType())
                .flatMap(actionType -> {
                    if (!validateActionPermission(player, game, actionType)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 행동을 할 수 없습니다"));
                    }
                    return replaceAction(game, dto);
                });
    }

    private Mono<ActionType> parseActionType(String typeString) {
        try {
            return Mono.just(ActionType.valueOf(typeString));
        } catch (IllegalArgumentException e) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 행동 타입입니다"));
        }
    }

    private Mono<Void> replaceAction(GameEntity game, RegisterActionDto dto) {
        return gameActionRepository.deleteByGameIdAndActorUserIdAndDayCountAndType(
                        game.getId(), dto.getActorUserId(), game.getDayCount(), dto.getType())
                .then(Mono.defer(() -> createAndSaveAction(game, dto)));
    }

    private Mono<Void> createAndSaveAction(GameEntity game, RegisterActionDto dto) {
        String actionId = UuidCreator.getTimeOrderedEpoch().toString();
        GameActionEntity action = GameActionEntity.builder()
                .id(actionId)
                .gameId(game.getId())
                .dayCount(game.getDayCount())
                .phase(game.getCurrentPhase())
                .type(dto.getType())
                .actorUserId(dto.getActorUserId())
                .targetUserId(dto.getTargetUserId())
                .createdAt(LocalDateTime.now())
                .build();
        return gameActionRepository.save(action).then();
    }

    private boolean validateActionPermission(GamePlayerEntity player, GameEntity game, ActionType actionType) {
        if (!player.getIsAlive()) {
            return false;
        }

        GamePhase currentPhase = game.getCurrentPhaseAsEnum();
        PlayerRole playerRole = player.getRoleAsEnum();

        return switch (actionType) {
            case VOTE -> currentPhase == GamePhase.VOTE;
            case MAFIA_KILL -> currentPhase == GamePhase.NIGHT && playerRole == PlayerRole.MAFIA;
            case DOCTOR_HEAL -> currentPhase == GamePhase.NIGHT && playerRole == PlayerRole.DOCTOR;
            case POLICE_CHECK -> currentPhase == GamePhase.NIGHT && playerRole == PlayerRole.POLICE;
        };
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
                .flatMap(winner -> {
                    if (winner != null) {
                        return endGame(game, winner, result);
                    } else {
                        return moveToNextPhase(game, result);
                    }
                });
    }

    private Mono<NextPhaseResponse> endGame(GameEntity game, String winner, NextPhaseResponse.PhaseResult result) {
        game.setWinnerTeam(winner);
        game.setFinishedAt(LocalDateTime.now());
        return gameRepository.save(game)
                .thenReturn(buildNextPhaseResponse(game, result));
    }

    private Mono<NextPhaseResponse> moveToNextPhase(GameEntity game, NextPhaseResponse.PhaseResult result) {
        updateToNextPhase(game);
        return gameRepository.save(game)
                .thenReturn(buildNextPhaseResponse(game, result));
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
                .collectList()
                .map(this::selectTargetFromVotes);
    }

    private Mono<String> getDoctorHealTarget(GameEntity game) {
        return gameActionRepository.findByGameIdAndDayCountAndType(
                        game.getId(), game.getDayCount(), ActionType.DOCTOR_HEAL.toString())
                .next()
                .map(GameActionEntity::getTargetUserId)
                .defaultIfEmpty("");
    }

    private Mono<NextPhaseResponse.PhaseResult> executeNightKill(GameEntity game, String mafiaTarget, String doctorTarget) {
        List<String> deaths = new ArrayList<>();

        // 마피아가 타겟을 선택하지 않았거나, 의사가 살린 경우 사망 없음
        if (mafiaTarget == null || mafiaTarget.isEmpty() || mafiaTarget.equals(doctorTarget)) {
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
        return gameActionRepository.findByGameIdAndDayCountAndType(
                        game.getId(), game.getDayCount(), ActionType.VOTE.toString())
                .collectList()
                .map(this::selectTargetFromVotes);
    }

    private String selectTargetFromVotes(List<GameActionEntity> actions) {
        if (actions.isEmpty()) {
            return null;
        }

        Map<String, Long> voteCount = actions.stream()
                .collect(Collectors.groupingBy(
                        GameActionEntity::getTargetUserId,
                        Collectors.counting()));

        long maxVotes = voteCount.values().stream()
                .max(Long::compareTo)
                .orElse(0L);

        List<String> topVoted = voteCount.entrySet().stream()
                .filter(entry -> entry.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .toList();

        // 동점이면 null 반환 (처형/살해 없음)
        return topVoted.size() == 1 ? topVoted.get(0) : null;
    }

    private Mono<Void> killPlayer(String gameId, String userId) {
        return gamePlayerRepository.findByGameIdAndUserId(gameId, userId)
                .flatMap(player -> {
                    player.setIsAlive(false);
                    player.setDiedAt(LocalDateTime.now());
                    return gamePlayerRepository.save(player);
                })
                .then();
    }

    private Mono<String> checkGameEnd(String gameId) {
        Mono<Long> aliveMafiaMono = gamePlayerRepository.countByGameIdAndIsAliveAndRole(
                gameId, true, PlayerRole.MAFIA.toString());

        // 전체 생존자 - 마피아 = 시민팀 (시민 + 의사 + 경찰)
        Mono<Long> aliveCitizensTeamMono = gamePlayerRepository.countByGameIdAndIsAlive(gameId, true)
                .zipWith(aliveMafiaMono)
                .map(tuple -> tuple.getT1() - tuple.getT2());

        return Mono.zip(aliveMafiaMono, aliveCitizensTeamMono)
                .map(tuple -> {
                    long aliveMafia = tuple.getT1();
                    long aliveCitizensTeam = tuple.getT2();

                    if (aliveMafia == 0) {
                        return "CITIZEN"; // 시민팀 승리
                    } else if (aliveMafia >= aliveCitizensTeam) {
                        return "MAFIA"; // 마피아 승리
                    }
                    return null; // 게임 계속
                });
    }

    private void updateToNextPhase(GameEntity game) {
        GamePhase currentPhase = game.getCurrentPhaseAsEnum();
        LocalDateTime now = LocalDateTime.now();

        switch (currentPhase) {
            case NIGHT -> {
                game.setCurrentPhaseFromEnum(GamePhase.DAY);
                game.setPhaseDurationSeconds(DAY_DURATION);
            }
            case DAY -> {
                game.setCurrentPhaseFromEnum(GamePhase.VOTE);
                game.setPhaseDurationSeconds(VOTE_DURATION);
            }
            case VOTE -> {
                game.setCurrentPhaseFromEnum(GamePhase.DEFENSE);
                game.setPhaseDurationSeconds(DEFENSE_DURATION);
            }
            case DEFENSE -> {
                game.setCurrentPhaseFromEnum(GamePhase.RESULT);
                game.setPhaseDurationSeconds(RESULT_DURATION);
            }
            case RESULT -> {
                game.setCurrentPhaseFromEnum(GamePhase.NIGHT);
                game.setDayCount(game.getDayCount() + 1);
                game.setPhaseDurationSeconds(NIGHT_DURATION);
            }
        }

        game.setPhaseStartTime(now);
    }

    private NextPhaseResponse buildNextPhaseResponse(GameEntity game, NextPhaseResponse.PhaseResult result) {
        result.setWinnerTeam(game.getWinnerTeam());

        return NextPhaseResponse.builder()
                .currentPhase(game.getCurrentPhase())
                .dayCount(game.getDayCount())
                .phaseStartTime(game.getPhaseStartTime())
                .phaseDurationSeconds(game.getPhaseDurationSeconds())
                .lastPhaseResult(result)
                .build();
    }

    private Long calculateRemainingSeconds(GameEntity game) {
        if (game.getPhaseStartTime() == null || game.getPhaseDurationSeconds() == null) {
            return 0L;
        }

        LocalDateTime endTime = game.getPhaseStartTime().plusSeconds(game.getPhaseDurationSeconds());
        long remaining = Duration.between(LocalDateTime.now(), endTime).getSeconds();
        return Math.max(0, remaining);
    }
}
