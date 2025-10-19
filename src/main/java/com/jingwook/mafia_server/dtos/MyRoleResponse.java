package com.jingwook.mafia_server.dtos;

import com.jingwook.mafia_server.enums.PlayerRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MyRoleResponse {
    private PlayerRole role;
    private Boolean isAlive;
    private Integer position;
}
