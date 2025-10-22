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
    private Map<String, Long> voteCount;
    private String topVotedUserId;  // 최다득표자 (동점이면 null)
    private Long topVoteCount;      // 최다 득표수
    private Boolean hasMajority;    // 과반 득표 여부

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class VoteInfo {
        private String voterUserId;
        private String targetUserId;
    }
}
