package com.jingwook.mafia_server.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class CreateRoomDto {
    private String username;
    private String roomName;
}
