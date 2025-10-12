package com.jingwook.mafia_server.controllers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SessionRequestDto {
    @NotBlank(message = "SessionId is required")
    @NotNull(message = "SessionId is required")
    private String sessionId;

}
