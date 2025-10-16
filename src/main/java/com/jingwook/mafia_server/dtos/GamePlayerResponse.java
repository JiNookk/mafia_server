package com.jingwook.mafia_server.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GamePlayerResponse {
    private Long userId;
    private String username;
    private Integer position;
    private Boolean isAlive;
    private LocalDateTime diedAt;
}
