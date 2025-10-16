package com.jingwook.mafia_server.enums;

import java.util.Arrays;

public enum GamePhase {
    NIGHT("밤"),
    DAY("낮"),
    VOTE("투표"),
    DEFENSE("최후변론"),
    RESULT("결과");

    private final String korean;

    GamePhase(String korean) {
        this.korean = korean;
    }

    public String getKorean(){
        return korean;
    }

    public static GamePhase fromKorean(String korean){
        return Arrays.stream(values())
                .filter(phase -> phase.korean.equals(korean))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 페이즈입니다: " + korean
                ));
    }
}
