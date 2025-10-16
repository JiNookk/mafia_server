package com.jingwook.mafia_server.services;

import com.jingwook.mafia_server.dtos.*;
import com.jingwook.mafia_server.entities.GameActionEntity;
import com.jingwook.mafia_server.entities.GameEntity;
import com.jingwook.mafia_server.entities.GamePlayerEntity;
import com.jingwook.mafia_server.entities.RoomMemberEntity;
import com.jingwook.mafia_server.enums.ActionType;
import com.jingwook.mafia_server.enums.GamePhase;
import com.jingwook.mafia_server.enums.PlayerRole;
import com.jingwook.mafia_server.repositories.*;
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
            UserRepository userRepository) {
        this.gameRepository = gameRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.gameActionRepository = gameActionRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Mono<GameStateResponse> startGame(Long roomId) {
        return checkNoActiveGame(roomId)
                .then(createNewGame(roomId))
                .flatMap(game -> initializeGamePlayers(game, roomId))
                .map(this::buildGameStateResponse);
    }

    private Mono<Void> checkNoActiveGame(Long roomId) {
        return gameRepository.findActiveGameByRoomId(roomId)
                .flatMap(existingGame -> Mono.<Void>error(
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "게임이 이미 진행 중입니다")))
                .then();
    }

    private Mono<GameEntity> createNewGame(Long roomId) {
        LocalDateTime now = LocalDateTime.now();
        GameEntity game = GameEntity.builder()
                .roomId(roomId)
                .currentPhase(GamePhase.NIGHT.toString())
                .dayCount(1)
                .phaseStartTime(now)
                .phaseDurationSeconds(NIGHT_DURATION)
                .startedAt(now)
                .build();
        return gameRepository.save(game);
    }

    private Mono<GameEntity> initializeGamePlayers(GameEntity game, Long roomId) {
        return roomMemberRepository.findByRoomId(roomId.toString())
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

    private Mono<Void> assignRolesAndSavePlayers(Long gameId, List<RoomMemberEntity> members) {
        // 8명 검증
        if (members.size() != 8) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "게임은 정확히 8명이 필요합니다. 현재: " + members.size() + "명"));
        }

        // 8명 기준 직업 배정: 마피아 2, 의사 1, 경찰 1, 시민 4
        List<PlayerRole> roles = Arrays.asList(
                PlayerRole.MAFIA, PlayerRole.MAFIA,
                PlayerRole.DOCTOR,
                PlayerRole.POLICE,
                PlayerRole.CITIZEN, PlayerRole.CITIZEN, PlayerRole.CITIZEN, PlayerRole.CITIZEN
        );

        // 랜덤 섞기
        Collections.shuffle(roles);

        List<GamePlayerEntity> players = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            RoomMemberEntity member = members.get(i);
            GamePlayerEntity player = GamePlayerEntity.builder()
                    .gameId(gameId)
                    .userId(Long.parseLong(member.getUserId()))
                    .role(roles.get(i).toString())
                    .isAlive(true)
                    .position(i + 1)
                    .build();
            players.add(player);
        }

        return gamePlayerRepository.saveAll(players).then();
    }

    public Mono<GameStateResponse> getGameState(Long gameId) {
        return gameRepository.findById(gameId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "게임을 찾을 수 없습니다")))
                .map(this::buildGameStateResponse);
    }

    public Mono<MyRoleResponse> getMyRole(Long gameId, Long userId) {
        return gamePlayerRepository.findByGameIdAndUserId(gameId, userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "게임 참가자를 찾을 수 없습니다")))
                .map(player -> MyRoleResponse.builder()
                        .role(player.getRole())
                        .isAlive(player.getIsAlive())
                        .position(player.getPosition())
                        .build());
    }

    public Mono<GamePlayersResponse> getPlayers(Long gameId) {
        return gamePlayerRepository.findByGameId(gameId)
                .flatMap(player -> userRepository.findById(player.getUserId().toString())
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
    public Mono<Void> registerAction(Long gameId, RegisterActionDto dto) {
        return gameRepository.findById(gameId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "게임을 찾을 수 없습니다")))
                .flatMap(game -> {
                    // 게임 종료 체크
                    if (game.isFinished()) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "종료된 게임입니다"));
                    }

                    return gamePlayerRepository.findByGameIdAndUserId(gameId, dto.getActorUserId())
                            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "게임 참가자가 아닙니다")))
                            .flatMap(player -> {
                                // enum 검증
                                ActionType actionType;
                                try {
                                    actionType = ActionType.valueOf(dto.getType());
                                } catch (IllegalArgumentException e) {
                                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 행동 타입입니다"));
                                }

                                // 권한 검증
                                if (!validateActionPermission(player, game, actionType)) {
                                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 행동을 할 수 없습니다"));
                                }

                                // 기존 행동 삭제 후 새로 등록 (변경 가능하도록)
                                return gameActionRepository.deleteByGameIdAndActorUserIdAndDayCountAndType(
                                        gameId, dto.getActorUserId(), game.getDayCount(), dto.getType())
                                        .then(Mono.defer(() -> {
                                            GameActionEntity action = GameActionEntity.builder()
                                                    .gameId(gameId)
                                                    .dayCount(game.getDayCount())
                                                    .phase(game.getCurrentPhase())
                                                    .type(dto.getType())
                                                    .actorUserId(dto.getActorUserId())
                                                    .targetUserId(dto.getTargetUserId())
                                                    .createdAt(LocalDateTime.now())
                                                    .build();
                                            return gameActionRepository.save(action);
                                        }));
                            });
                })
                .then();
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

    public Mono<VoteStatusResponse> getVoteStatus(Long gameId, Integer dayCount) {
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

                    Map<Long, Long> voteCount = votes.stream()
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
    public Mono<NextPhaseResponse> nextPhase(Long gameId) {
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
        Mono<Long> mafiaTargetMono = getMafiaKillTarget(game);
        Mono<Long> doctorTargetMono = getDoctorHealTarget(game);

        return Mono.zip(mafiaTargetMono, doctorTargetMono)
                .flatMap(tuple -> executeNightKill(game, tuple.getT1(), tuple.getT2()));
    }

    private Mono<Long> getMafiaKillTarget(GameEntity game) {
        return gameActionRepository.findByGameIdAndDayCountAndType(
                        game.getId(), game.getDayCount(), ActionType.MAFIA_KILL.toString())
                .collectList()
                .map(this::selectTargetFromVotes);
    }

    private Mono<Long> getDoctorHealTarget(GameEntity game) {
        return gameActionRepository.findByGameIdAndDayCountAndType(
                        game.getId(), game.getDayCount(), ActionType.DOCTOR_HEAL.toString())
                .next()
                .map(GameActionEntity::getTargetUserId)
                .defaultIfEmpty(-1L);
    }

    private Mono<NextPhaseResponse.PhaseResult> executeNightKill(GameEntity game, Long mafiaTarget, Long doctorTarget) {
        List<Long> deaths = new ArrayList<>();

        // 마피아가 타겟을 선택하지 않았거나, 의사가 살린 경우 사망 없음
        if (mafiaTarget == null || mafiaTarget.equals(doctorTarget)) {
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
                    if (executedUserId == null) {
                        return Mono.just(NextPhaseResponse.PhaseResult.builder().build());
                    }

                    return killPlayer(game.getId(), executedUserId)
                            .thenReturn(NextPhaseResponse.PhaseResult.builder()
                                    .executedUserId(executedUserId)
                                    .build());
                });
    }

    private Mono<Long> getExecutedUserIdFromVotes(GameEntity game) {
        return gameActionRepository.findByGameIdAndDayCountAndType(
                        game.getId(), game.getDayCount(), ActionType.VOTE.toString())
                .collectList()
                .map(this::selectTargetFromVotes);
    }

    private Long selectTargetFromVotes(List<GameActionEntity> actions) {
        if (actions.isEmpty()) {
            return null;
        }

        Map<Long, Long> voteCount = actions.stream()
                .collect(Collectors.groupingBy(
                        GameActionEntity::getTargetUserId,
                        Collectors.counting()));

        long maxVotes = voteCount.values().stream()
                .max(Long::compareTo)
                .orElse(0L);

        List<Long> topVoted = voteCount.entrySet().stream()
                .filter(entry -> entry.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .toList();

        // 동점이면 null 반환 (처형/살해 없음)
        return topVoted.size() == 1 ? topVoted.get(0) : null;
    }

    private Mono<Void> killPlayer(Long gameId, Long userId) {
        return gamePlayerRepository.findByGameIdAndUserId(gameId, userId)
                .flatMap(player -> {
                    player.setIsAlive(false);
                    player.setDiedAt(LocalDateTime.now());
                    return gamePlayerRepository.save(player);
                })
                .then();
    }

    private Mono<String> checkGameEnd(Long gameId) {
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
