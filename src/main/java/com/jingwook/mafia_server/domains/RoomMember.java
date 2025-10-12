package com.jingwook.mafia_server.domains;

import java.util.HashMap;
import java.util.Map;

import com.jingwook.mafia_server.enums.ParticipatingRole;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class RoomMember {
    @NotNull(message = "RoomMember has no userId")
    @NotBlank(message = "RoomMember has no userId")
    private String userId;
    @NotNull(message = "RoomMember has no roomId")
    @NotBlank(message = "RoomMember has no roomId")
    private String roomId;
    @NotNull(message = "RoomMember has no role")
    private ParticipatingRole role;

    public RoomMember(@Valid String userId, @Valid String roomId, @Valid ParticipatingRole role) {
        this.userId = userId;
        this.roomId = roomId;
        this.role = role;
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        map.put("userId", userId);
        map.put("roomId", roomId);
        map.put("role", role.toString());
        return map;
    }
}
