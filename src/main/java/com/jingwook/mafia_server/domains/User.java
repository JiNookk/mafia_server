package com.jingwook.mafia_server.domains;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class User {
    private final String sessionId;
    private final String nickname;
    private final LocalDateTime joinedAt;

    public User(String sessionId, String nickname, LocalDateTime joinedAt) {
        this.sessionId = sessionId;
        this.nickname = nickname;
        this.joinedAt = joinedAt;
    }
}