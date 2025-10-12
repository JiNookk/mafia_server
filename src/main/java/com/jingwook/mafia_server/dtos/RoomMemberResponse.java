package com.jingwook.mafia_server.dtos;

import com.jingwook.mafia_server.enums.ParticipatingRole;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomMemberResponse {
    private String playerId;
    private ParticipatingRole role;
}
