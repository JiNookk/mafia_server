package com.jingwook.mafia_server.dtos;

import com.jingwook.mafia_server.enums.PlayerRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PoliceCheckResultResponse {
    private List<CheckResult> results;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CheckResult {
        private String targetUserId;
        private String targetUsername;
        private PlayerRole targetRole;
        private Integer dayCount;
    }
}
