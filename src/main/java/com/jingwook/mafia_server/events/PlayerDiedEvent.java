package com.jingwook.mafia_server.events;

import com.jingwook.mafia_server.enums.DeathReason;
import lombok.Getter;

import java.util.List;

@Getter
public class PlayerDiedEvent {
    private final String roomId;
    private final String gameId;
    private final List<String> deadPlayerIds;
    private final DeathReason reason;

    public PlayerDiedEvent(String roomId, String gameId, List<String> deadPlayerIds, DeathReason reason) {
        this.roomId = roomId;
        this.gameId = gameId;
        this.deadPlayerIds = deadPlayerIds;
        this.reason = reason;
    }
}
