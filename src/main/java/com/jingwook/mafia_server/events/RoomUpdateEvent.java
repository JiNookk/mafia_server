package com.jingwook.mafia_server.events;

import com.jingwook.mafia_server.dtos.RoomDetailResponse;
import lombok.Getter;

@Getter
public class RoomUpdateEvent {
    private final String roomId;
    private final RoomDetailResponse roomDetail;

    public RoomUpdateEvent(String roomId, RoomDetailResponse roomDetail) {
        this.roomId = roomId;
        this.roomDetail = roomDetail;
    }
}
