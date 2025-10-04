package com.jingwook.mafia_server.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupDto {
    private String nickname;

    public SignupDto() {}

    public SignupDto(String nickname) {
        this.nickname = nickname;
    }

}
