package com.jingwook.mafia_server.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MyRoleResponse {
    private String role;
    private Boolean isAlive;
    private Integer position;
}
