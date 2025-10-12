package com.jingwook.mafia_server.domains;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class User {
    @NotNull(message = "User has no sessionId")
    @NotBlank(message = "User has no sessionId")
    private final String sessionId;
    @NotNull(message = "User has no nickname")
    @NotBlank(message = "User has no nickname")
    private final String nickname;
    @NotNull(message = "User has no joinedAt")
    private final LocalDateTime joinedAt;

    public User(@Valid String sessionId, @Valid String nickname, @Valid LocalDateTime joinedAt) {
        this.sessionId = sessionId;
        this.nickname = nickname;
        this.joinedAt = joinedAt;
    }

}