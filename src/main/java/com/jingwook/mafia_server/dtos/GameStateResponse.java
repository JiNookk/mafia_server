package com.jingwook.mafia_server.dtos;

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
    private Long gameId;
    private String currentPhase;
    private Integer dayCount;
    private LocalDateTime phaseStartTime;
    private Integer phaseDurationSeconds;
    private Long remainingSeconds;
    private String winnerTeam;
    private LocalDateTime finishedAt;
}
