package com.jingwook.mafia_server.domains;

import com.jingwook.mafia_server.enums.GamePhase;
import com.jingwook.mafia_server.enums.Team;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

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

    public Game(
            @Valid String id,
            @Valid String roomId,
            @Valid GamePhase currentPhase,
            @Valid Integer dayCount,
            LocalDateTime phaseStartTime,
            Integer phaseDurationSeconds,
            Team winnerTeam,
            @Valid LocalDateTime startedAt,
            LocalDateTime finishedAt) {
        this.id = id;
        this.roomId = roomId;
        this.currentPhase = currentPhase;
        this.dayCount = dayCount;
        this.phaseStartTime = phaseStartTime;
        this.phaseDurationSeconds = phaseDurationSeconds;
        this.winnerTeam = winnerTeam;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
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
     * @param hasExecutedTarget 처형 대상자가 있는지 여부
     * @return 다음 페이즈로 전환된 새 Game 객체
     */
    public Game transitionToNextPhase(Map<GamePhase, Integer> phaseDurations, boolean hasExecutedTarget) {
        GamePhase nextPhase = getNextPhase(currentPhase, hasExecutedTarget);
        Integer nextPhaseDuration = phaseDurations.get(nextPhase);
        LocalDateTime now = LocalDateTime.now();

        // NIGHT으로 돌아갈 때 날짜 증가 (RESULT 이후 또는 VOTE에서 처형 없이 직행)
        int nextDayCount = ((currentPhase == GamePhase.RESULT || currentPhase == GamePhase.VOTE) && nextPhase == GamePhase.NIGHT)
                ? dayCount + 1
                : dayCount;

        return new Game(
                id,
                roomId,
                nextPhase,
                nextDayCount,
                now,
                nextPhaseDuration,
                winnerTeam,
                startedAt,
                finishedAt
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
                LocalDateTime.now()
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
        } else if (aliveMafia >= aliveCitizens) {
            return Optional.of(Team.MAFIA); // 마피아 승리
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
}
