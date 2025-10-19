package com.jingwook.mafia_server.domains;

import com.jingwook.mafia_server.enums.ActionType;
import com.jingwook.mafia_server.enums.GamePhase;
import com.jingwook.mafia_server.enums.PlayerRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class GamePlayer {
    @NotNull(message = "GamePlayer has no id")
    @NotBlank(message = "GamePlayer has no id")
    private final String id;

    @NotNull(message = "GamePlayer has no gameId")
    @NotBlank(message = "GamePlayer has no gameId")
    private final String gameId;

    @NotNull(message = "GamePlayer has no userId")
    @NotBlank(message = "GamePlayer has no userId")
    private final String userId;

    @NotNull(message = "GamePlayer has no role")
    private final PlayerRole role;

    @NotNull(message = "GamePlayer has no isAlive")
    private final Boolean isAlive;

    @NotNull(message = "GamePlayer has no position")
    private final Integer position;

    private final LocalDateTime diedAt;

    public GamePlayer(
            @Valid String id,
            @Valid String gameId,
            @Valid String userId,
            @Valid PlayerRole role,
            @Valid Boolean isAlive,
            @Valid Integer position,
            LocalDateTime diedAt) {
        this.id = id;
        this.gameId = gameId;
        this.userId = userId;
        this.role = role;
        this.isAlive = isAlive;
        this.position = position;
        this.diedAt = diedAt;
    }

    /**
     * 플레이어가 특정 행동을 할 수 있는 권한이 있는지 검증
     * @param currentPhase 현재 게임 페이즈
     * @param actionType 행동 타입
     * @return 권한 유무
     */
    public boolean canPerformAction(GamePhase currentPhase, ActionType actionType) {
        // 죽은 플레이어는 행동할 수 없음
        if (!isAlive) {
            return false;
        }

        return switch (actionType) {
            case VOTE -> currentPhase == GamePhase.VOTE;
            case MAFIA_KILL -> currentPhase == GamePhase.NIGHT && role == PlayerRole.MAFIA;
            case DOCTOR_HEAL -> currentPhase == GamePhase.NIGHT && role == PlayerRole.DOCTOR;
            case POLICE_CHECK -> currentPhase == GamePhase.NIGHT && role == PlayerRole.POLICE;
        };
    }

    /**
     * 플레이어가 마피아팀인지 확인
     */
    public boolean isMafia() {
        return role == PlayerRole.MAFIA;
    }

    /**
     * 플레이어가 시민팀인지 확인 (시민, 의사, 경찰)
     */
    public boolean isCitizen() {
        return role == PlayerRole.CITIZEN || role == PlayerRole.DOCTOR || role == PlayerRole.POLICE;
    }

    /**
     * 플레이어 사망 처리된 새로운 GamePlayer 객체 반환 (불변)
     */
    public GamePlayer die() {
        return new GamePlayer(
                id,
                gameId,
                userId,
                role,
                false,
                position,
                LocalDateTime.now()
        );
    }
}
