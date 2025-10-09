package com.jingwook.mafia_server.dtos;

import com.jingwook.mafia_server.enums.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class RoomListResponse {
    private String id;
    private String name;
    private Integer currentPlayers;
    private Integer maxPlayers;
    private RoomStatus status;
}
