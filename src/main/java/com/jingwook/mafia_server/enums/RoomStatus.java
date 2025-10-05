package com.jingwook.mafia_server.enums;

import java.util.Arrays;

public enum RoomStatus {
    AVAILABLE("대기중"),
    STARTED("게임중"),
    FULL("풀방");

    private final String korean;

    RoomStatus(String korean) {
        this.korean = korean;
    }

    public String getKorean(){
        return korean;
    }

    public static RoomStatus fromKorean(String korean){
        return Arrays.stream(values())
                .filter(status -> status.korean.equals(korean))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 상태입니다: " + korean
                ));
    }
}
