package com.jingwook.mafia_server.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VoteStatusResponse {
    private List<VoteInfo> votes;
    private Map<Long, Long> voteCount;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class VoteInfo {
        private Long voterUserId;
        private Long targetUserId;
    }
}
