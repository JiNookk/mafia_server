package com.jingwook.mafia_server.domains;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.jingwook.mafia_server.enums.RoomStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class Room {
    @NotNull(message = "Room has no id")
    @NotBlank(message = "Room has no id")
    private String id;
    @NotNull(message = "Room has no name")
    @NotBlank(message = "Room has no name")
    private String name;
    @NotNull(message = "Room has no currentPlayers")
    private Integer currentPlayers;
    @NotNull(message = "Room has no maxPlayers")
    private Integer maxPlayers;
    @NotNull(message = "Room has no status")
    private RoomStatus status;
    @NotNull(message = "Room has no hostId")
    @NotBlank(message = "Room has no hostId")
    private String hostId;
    @NotNull(message = "Room has no createdAt")
    private LocalDateTime createdAt;

    public Room(@Valid String id, @Valid String name, @Valid Integer currentPlayers, @Valid Integer maxPlayers,
            @Valid RoomStatus status, @Valid String hostId, @Valid LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.currentPlayers = currentPlayers;
        this.maxPlayers = maxPlayers;
        this.status = status;
        this.hostId = hostId;
        this.createdAt = createdAt;

    }

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        map.put("id", getId());
        map.put("name", getName());
        map.put("host", getHostId());
        map.put("playerCount", String.valueOf(getCurrentPlayers()));
        map.put("maxPlayers", String.valueOf(getMaxPlayers()));
        map.put("status", getStatus().toString());
        map.put("createdAt", String.valueOf(getCreatedAt().toLocalDate()));
        return map;
    }

}