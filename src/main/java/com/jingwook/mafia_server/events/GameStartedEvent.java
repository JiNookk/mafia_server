package com.jingwook.mafia_server.events;

import lombok.Getter;

@Getter
public class GameStartedEvent {
    private final String roomId;
    private final String gameId;

    public GameStartedEvent(String roomId, String gameId) {
        this.roomId = roomId;
        this.gameId = gameId;
    }
}
