package com.jingwook.mafia_server.events;

import com.jingwook.mafia_server.dtos.NextPhaseResponse;
import lombok.Getter;

@Getter
public class PhaseChangedEvent {
    private final String roomId;
    private final String gameId;
    private final NextPhaseResponse phaseData;

    public PhaseChangedEvent(String roomId, String gameId, NextPhaseResponse phaseData) {
        this.roomId = roomId;
        this.gameId = gameId;
        this.phaseData = phaseData;
    }
}
