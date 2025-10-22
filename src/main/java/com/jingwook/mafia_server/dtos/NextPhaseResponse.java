package com.jingwook.mafia_server.dtos;

import com.jingwook.mafia_server.enums.GamePhase;
import com.jingwook.mafia_server.enums.Team;
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
    private GamePhase currentPhase;
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
        private Team winnerTeam;
        private Boolean wasSavedByDoctor; // 의사 구출 성공 여부 (NIGHT -> DAY 전환 시)
    }
}
