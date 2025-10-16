package com.jingwook.mafia_server.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SessionResponseDto {
    private String userId;
    private String nickname;
    private String roomId;

    public SessionResponseDto(){}

    public SessionResponseDto(String userId, String nickname){
        this.userId = userId;
        this.nickname = nickname;
    }

    public SessionResponseDto(String userId, String nickname, String roomId){
        this.userId = userId;
        this.nickname = nickname;
        this.roomId = roomId;
    }
}
