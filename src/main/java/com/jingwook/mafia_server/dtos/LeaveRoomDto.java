package com.jingwook.mafia_server.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class LeaveRoomDto {
    @NotBlank(message = "User ID is required")
    @NotNull(message = "User ID is required")
    private String userId;
}
