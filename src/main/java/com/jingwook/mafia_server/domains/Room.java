package com.jingwook.mafia_server.domains;


import com.jingwook.mafia_server.enums.RoomStatus;
import lombok.Getter;

@Getter
public class Room {
    private String id;
    private String name;
    private Integer currentPlayers;
    private Integer maxPlayers;
    private RoomStatus status;

    public Room(String id, String name, Integer currentPlayers, Integer maxPlayers, RoomStatus status) {
        this.id = id;
        this.name = name;
        this.currentPlayers = currentPlayers;
        this.maxPlayers = maxPlayers;
        this.status = status;
    }
}