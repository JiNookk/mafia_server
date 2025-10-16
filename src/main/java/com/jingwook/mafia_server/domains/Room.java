package com.jingwook.mafia_server.domains;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    @NotNull(message = "Room has no maxPlayers")
    private Integer maxPlayers;
    @NotNull(message = "Room has no status")
    private RoomStatus status;
    @NotNull(message = "Room has no hostUserId")
    @NotBlank(message = "Room has no hostUserId")
    private String hostUserId;
    @NotNull(message = "Room has no createdAt")
    private LocalDateTime createdAt;
    @NotNull(message = "Room has no members")
    private List<RoomMember> members;

    public Room(@Valid String id, @Valid String name, @Valid Integer maxPlayers,
            @Valid RoomStatus status, @Valid String hostUserId, @Valid LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.maxPlayers = maxPlayers;
        this.status = status;
        this.hostUserId = hostUserId;
        this.createdAt = createdAt;
        this.members = new ArrayList<>();
    }

    public Room(@Valid String id, @Valid String name, @Valid Integer maxPlayers,
            @Valid RoomStatus status, @Valid String hostUserId, @Valid LocalDateTime createdAt,
            @Valid List<RoomMember> members) {
        this.id = id;
        this.name = name;
        this.maxPlayers = maxPlayers;
        this.status = status;
        this.hostUserId = hostUserId;
        this.createdAt = createdAt;
        this.members = members != null ? new ArrayList<>(members) : new ArrayList<>();
    }

    /**
     * 현재 참여 중인 플레이어 수를 반환합니다.
     */
    public int getCurrentPlayerCount() {
        return members.size();
    }

    /**
     * 도메인 레벨에서 실제 방 상태를 계산합니다.
     * STARTED 상태가 아닌 경우, 현재 플레이어 수를 기반으로 FULL/AVAILABLE을 결정합니다.
     */
    public RoomStatus calculateActualStatus() {
        // 게임이 시작된 경우 그대로 유지
        if (status == RoomStatus.STARTED) {
            return RoomStatus.STARTED;
        }

        // 현재 플레이어 수가 최대 인원과 같거나 크면 FULL
        if (getCurrentPlayerCount() >= maxPlayers) {
            return RoomStatus.FULL;
        }

        // 그 외에는 AVAILABLE
        return RoomStatus.AVAILABLE;
    }

    /**
     * 방이 가득 찼는지 확인합니다.
     */
    public boolean isFull() {
        return getCurrentPlayerCount() >= maxPlayers;
    }

    /**
     * 게임이 시작되었는지 확인합니다.
     */
    public boolean isStarted() {
        return status == RoomStatus.STARTED;
    }

    /**
     * 멤버 목록을 불변 리스트로 반환합니다.
     */
    public List<RoomMember> getMembers() {
        return Collections.unmodifiableList(members);
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        map.put("id", getId());
        map.put("name", getName());
        map.put("host", getHostUserId());
        map.put("maxPlayers", String.valueOf(getMaxPlayers()));
        map.put("status", getStatus().toString());
        map.put("createdAt", String.valueOf(getCreatedAt().toLocalDate()));
        return map;
    }

}