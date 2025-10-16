package com.jingwook.mafia_server.exceptions;

public class RoomFullException extends RuntimeException {
    public RoomFullException(String errorString) {
        super(errorString);
    }
}
