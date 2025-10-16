package com.jingwook.mafia_server.controllers;

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
    @NotNull(message = "UserId is required")
    private String userId;

}
