package com.jingwook.mafia_server.enums;

import java.util.Arrays;

public enum PlayerRole {
    MAFIA("마피아"),
    DOCTOR("의사"),
    POLICE("경찰"),
    CITIZEN("시민");

    private final String korean;

    PlayerRole(String korean) {
        this.korean = korean;
    }

    public String getKorean(){
        return korean;
    }

    public static PlayerRole fromKorean(String korean){
        return Arrays.stream(values())
                .filter(role -> role.korean.equals(korean))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 역할입니다: " + korean
                ));
    }
}
