package com.jingwook.mafia_server.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NextPhaseResponse {
    private String currentPhase;
    private Integer dayCount;
    private LocalDateTime phaseStartTime;
    private Integer phaseDurationSeconds;
    private PhaseResult lastPhaseResult;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PhaseResult {
        private List<String> deaths;
        private String executedUserId;
        private String winnerTeam;
    }
}
