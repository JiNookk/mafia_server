package com.jingwook.mafia_server.domains;

import com.jingwook.mafia_server.enums.GamePhase;
import com.jingwook.mafia_server.enums.Team;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
public class Game {
    @NotNull(message = "Game has no id")
    @NotBlank(message = "Game has no id")
    private final String id;

    @NotNull(message = "Game has no roomId")
    @NotBlank(message = "Game has no roomId")
    private final String roomId;

    @NotNull(message = "Game has no currentPhase")
    private final GamePhase currentPhase;

    @NotNull(message = "Game has no dayCount")
    private final Integer dayCount;

    private final LocalDateTime phaseStartTime;
    private final Integer phaseDurationSeconds;
    private final Team winnerTeam;

    @NotNull(message = "Game has no startedAt")
    private final LocalDateTime startedAt;

    private final LocalDateTime finishedAt;

    private final String defendantUserId; // 재판 대상자

    public Game(
            @Valid String id,
            @Valid String roomId,
            @Valid GamePhase currentPhase,
            @Valid Integer dayCount,
            LocalDateTime phaseStartTime,
            Integer phaseDurationSeconds,
            Team winnerTeam,
            @Valid LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String defendantUserId) {
        this.id = id;
        this.roomId = roomId;
        this.currentPhase = currentPhase;
        this.dayCount = dayCount;
        this.phaseStartTime = phaseStartTime;
        this.phaseDurationSeconds = phaseDurationSeconds;
        this.winnerTeam = winnerTeam;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.defendantUserId = defendantUserId;
    }

    /**
     * 게임이 종료되었는지 확인
     */
    public boolean isFinished() {
        return this.finishedAt != null;
    }

    /**
     * 현재 페이즈가 만료되었는지 확인
     */
    public boolean isPhaseExpired() {
        if (phaseStartTime == null || phaseDurationSeconds == null) {
            return false;
        }
        LocalDateTime expireTime = phaseStartTime.plusSeconds(phaseDurationSeconds);
        return LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * 다음 페이즈로 전환된 새로운 Game 객체 반환 (불변)
     * @param phaseDurations 각 페이즈별 지속 시간 맵
     * @param executedUserId 처형 대상자 ID (VOTE 페이즈에서 설정)
     * @return 다음 페이즈로 전환된 새 Game 객체
     */
    public Game transitionToNextPhase(Map<GamePhase, Integer> phaseDurations, String executedUserId) {
        boolean hasExecutedTarget = executedUserId != null && !executedUserId.isEmpty();
        GamePhase nextPhase = getNextPhase(currentPhase, hasExecutedTarget);
        Integer nextPhaseDuration = phaseDurations.get(nextPhase);
        LocalDateTime now = LocalDateTime.now();

        // NIGHT으로 돌아갈 때 날짜 증가 (RESULT 이후 또는 VOTE에서 처형 없이 직행)
        int nextDayCount = ((currentPhase == GamePhase.RESULT || currentPhase == GamePhase.VOTE) && nextPhase == GamePhase.NIGHT)
                ? dayCount + 1
                : dayCount;

        // 재판 대상자 설정: VOTE -> DEFENSE 전환 시 설정, RESULT -> NIGHT 전환 시 초기화
        String nextDefendantUserId = (currentPhase == GamePhase.VOTE && hasExecutedTarget)
                ? executedUserId
                : ((currentPhase == GamePhase.RESULT && nextPhase == GamePhase.NIGHT) ? null : defendantUserId);

        return new Game(
                id,
                roomId,
                nextPhase,
                nextDayCount,
                now,
                nextPhaseDuration,
                winnerTeam,
                startedAt,
                finishedAt,
                nextDefendantUserId
        );
    }

    /**
     * 게임 종료 처리된 새로운 Game 객체 반환 (불변)
     * @param winner 승리 팀
     * @return 종료 처리된 새 Game 객체
     */
    public Game endGame(Team winner) {
        return new Game(
                id,
                roomId,
                currentPhase,
                dayCount,
                phaseStartTime,
                phaseDurationSeconds,
                winner,
                startedAt,
                LocalDateTime.now(),
                defendantUserId
        );
    }

    /**
     * 다음 페이즈 계산
     * @param current 현재 페이즈
     * @param hasExecutedTarget 처형 대상자가 있는지 여부
     */
    private GamePhase getNextPhase(GamePhase current, boolean hasExecutedTarget) {
        return switch (current) {
            case NIGHT -> GamePhase.DAY;
            case DAY -> GamePhase.VOTE;
            case VOTE -> hasExecutedTarget ? GamePhase.DEFENSE : GamePhase.NIGHT; // 처형 대상 없으면 바로 NIGHT
            case DEFENSE -> GamePhase.RESULT;
            case RESULT -> GamePhase.NIGHT;
        };
    }

    /**
     * 게임 승리 조건 판단
     * @param aliveMafia 생존한 마피아 수
     * @param aliveCitizens 생존한 시민팀 수
     * @return 승리 팀 (Optional.empty()면 게임 계속)
     */
    public static Optional<Team> determineWinner(long aliveMafia, long aliveCitizens) {
        if (aliveMafia == 0) {
            return Optional.of(Team.CITIZEN); // 시민팀 승리
        } else if (aliveMafia > aliveCitizens) {
            return Optional.of(Team.MAFIA); // 마피아 승리 (마피아가 더 많으면)
        }
        return Optional.empty(); // 게임 계속
    }

    /**
     * 밤 페이즈에서 플레이어가 죽는지 판단
     * @param mafiaTarget 마피아가 선택한 타겟
     * @param doctorTarget 의사가 선택한 타겟
     * @return 마피아 타겟이 살아남으면 true (의사가 구함)
     */
    public static boolean isSavedByDoctor(String mafiaTarget, String doctorTarget) {
        if (mafiaTarget == null || mafiaTarget.isEmpty()) {
            return true; // 마피아가 타겟을 선택하지 않음
        }
        return mafiaTarget.equals(doctorTarget); // 의사가 같은 타겟을 선택함
    }

    /**
     * 8인 게임을 위한 역할 생성 및 셔플
     * 마피아 2명, 의사 1명, 경찰 1명, 시민 4명
     * @return 셔플된 역할 리스트
     */
    public static java.util.List<com.jingwook.mafia_server.enums.PlayerRole> createShuffledRoles() {
        java.util.List<com.jingwook.mafia_server.enums.PlayerRole> roles = java.util.Arrays.asList(
                com.jingwook.mafia_server.enums.PlayerRole.MAFIA,
                com.jingwook.mafia_server.enums.PlayerRole.MAFIA,
                com.jingwook.mafia_server.enums.PlayerRole.DOCTOR,
                com.jingwook.mafia_server.enums.PlayerRole.POLICE,
                com.jingwook.mafia_server.enums.PlayerRole.CITIZEN,
                com.jingwook.mafia_server.enums.PlayerRole.CITIZEN,
                com.jingwook.mafia_server.enums.PlayerRole.CITIZEN,
                com.jingwook.mafia_server.enums.PlayerRole.CITIZEN
        );
        java.util.Collections.shuffle(roles);
        return roles;
    }

    /**
     * 현재 페이즈의 남은 시간 계산 (초)
     */
    public long calculateRemainingSeconds() {
        if (phaseStartTime == null || phaseDurationSeconds == null) {
            return 0L;
        }

        LocalDateTime endTime = phaseStartTime.plusSeconds(phaseDurationSeconds);
        long remaining = Duration.between(LocalDateTime.now(), endTime).getSeconds();
        return Math.max(0, remaining);
    }

    /**
     * 투표 결과에서 대상을 선택
     *
     * @param targetUserIds 투표 대상 유저 ID 리스트
     * @param allowTieBreak 동점일 때 첫 번째 선택 여부 (true: 마피아 투표, false: 일반 투표)
     */
    public static String selectTargetFromVotes(List<String> targetUserIds, boolean allowTieBreak) {
        if (targetUserIds.isEmpty()) {
            return "";
        }

        Map<String, Long> voteCount = targetUserIds.stream()
                .collect(Collectors.groupingBy(
                        targetId -> targetId,
                        Collectors.counting()));

        long maxVotes = voteCount.values().stream()
                .max(Long::compareTo)
                .orElse(0L);

        List<String> topVoted = voteCount.entrySet().stream()
                .filter(entry -> entry.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .toList();

        if (topVoted.isEmpty()) {
            return "";
        }

        // 동점 처리: 마피아는 항상 첫 번째 선택, 일반 투표는 동점 시 처형 없음
        return (allowTieBreak || topVoted.size() == 1) ? topVoted.get(0) : "";
    }

    /**
     * 과반 득표 여부를 확인하는 투표 결과 선택
     *
     * @param targetUserIds 투표 대상 유저 ID 리스트
     * @param alivePlayers  생존 플레이어 수
     */
    public static String selectTargetFromVotesWithMajority(List<String> targetUserIds, long alivePlayers) {
        if (targetUserIds.isEmpty()) {
            return "";
        }

        Map<String, Long> voteCount = targetUserIds.stream()
                .collect(Collectors.groupingBy(
                        targetId -> targetId,
                        Collectors.counting()));

        long majorityThreshold = (alivePlayers / 2) + 1; // 과반

        // 과반을 얻은 사람 찾기
        List<String> majorityVoted = voteCount.entrySet().stream()
                .filter(entry -> entry.getValue() >= majorityThreshold)
                .map(Map.Entry::getKey)
                .toList();

        // 과반을 얻은 사람이 없으면 처형 없음
        if (majorityVoted.isEmpty()) {
            return "";
        }

        // 과반을 얻은 사람이 여러 명이면 처형 없음 (이론상 불가능하지만 안전장치)
        if (majorityVoted.size() > 1) {
            return "";
        }

        return majorityVoted.get(0);
    }
}
