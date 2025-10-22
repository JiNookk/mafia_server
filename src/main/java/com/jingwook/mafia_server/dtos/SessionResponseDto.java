package com.jingwook.mafia_server.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SessionResponseDto {
    private String userId;
    private String nickname;
    private CurrentRoomDto currentRoom;

    public SessionResponseDto(){}

    public SessionResponseDto(String userId, String nickname){
        this.userId = userId;
        this.nickname = nickname;
    }

    public SessionResponseDto(String userId, String nickname, CurrentRoomDto currentRoom){
        this.userId = userId;
        this.nickname = nickname;
        this.currentRoom = currentRoom;
    }
}
