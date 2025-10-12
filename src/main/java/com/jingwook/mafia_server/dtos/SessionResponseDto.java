package com.jingwook.mafia_server.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SessionResponseDto {
    private String userId;
    private String nickname;

    public SessionResponseDto(){}

    public SessionResponseDto(String userId, String nickname){
        this.userId = userId;
        this.nickname = nickname;
    }
}
