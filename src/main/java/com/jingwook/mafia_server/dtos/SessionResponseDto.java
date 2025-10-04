package com.jingwook.mafia_server.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SessionResponseDto {
    private String sessionId;
    private String nickname;

    public SessionResponseDto(){}

    public SessionResponseDto(String sessionId, String nickname){
        this.sessionId = sessionId;
        this.nickname = nickname;
    }
}
