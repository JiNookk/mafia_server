package com.jingwook.mafia_server.domains;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class User {
    private final String id;
    @NotNull(message = "User has no nickname")
    @NotBlank(message = "User has no nickname")
    private final String nickname;
    @NotNull(message = "User has no joinedAt")
    private final LocalDateTime joinedAt;

    public User(String id, @Valid String nickname, @Valid LocalDateTime joinedAt) {
        this.id = id;
        this.nickname = nickname;
        this.joinedAt = joinedAt;
    }

}