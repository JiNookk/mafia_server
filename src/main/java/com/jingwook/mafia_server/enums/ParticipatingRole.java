package com.jingwook.mafia_server.enums;

public enum ParticipatingRole {
    HOST("방장"),
    PARTICIPANT("참여자");

    private final String korean;

    ParticipatingRole(String korean){
        this.korean = korean;
    }

}
