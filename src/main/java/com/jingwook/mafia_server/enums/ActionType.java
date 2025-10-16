package com.jingwook.mafia_server.enums;

import java.util.Arrays;

public enum ActionType {
    VOTE("투표"),
    MAFIA_KILL("마피아 살해"),
    DOCTOR_HEAL("의사 치료"),
    POLICE_CHECK("경찰 조사");

    private final String korean;

    ActionType(String korean) {
        this.korean = korean;
    }

    public String getKorean(){
        return korean;
    }

    public static ActionType fromKorean(String korean){
        return Arrays.stream(values())
                .filter(type -> type.korean.equals(korean))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 행동 타입입니다: " + korean
                ));
    }
}
