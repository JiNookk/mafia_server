package com.jingwook.mafia_server.events;

import com.jingwook.mafia_server.enums.Team;
import lombok.Getter;

@Getter
public class GameEndedEvent {
    private final String roomId;
    private final String gameId;
    private final Team winnerTeam;

    public GameEndedEvent(String roomId, String gameId, Team winnerTeam) {
        this.roomId = roomId;
        this.gameId = gameId;
        this.winnerTeam = winnerTeam;
    }
}
