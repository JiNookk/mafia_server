package com.jingwook.mafia_server.dtos;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomDetailResponse {
    private String id;
    private String name;
    private List<RoomMemberResponse> members;
    private Integer currentPlayers;
    private Integer maxPlayers;
}
