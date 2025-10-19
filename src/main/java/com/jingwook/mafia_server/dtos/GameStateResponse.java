package com.jingwook.mafia_server.dtos;

import com.jingwook.mafia_server.enums.GamePhase;
import com.jingwook.mafia_server.enums.Team;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameStateResponse {
    private String gameId;
    private GamePhase currentPhase;
    private Integer dayCount;
    private LocalDateTime phaseStartTime;
    private Integer phaseDurationSeconds;
    private Long remainingSeconds;
    private Team winnerTeam;
    private LocalDateTime finishedAt;
}
