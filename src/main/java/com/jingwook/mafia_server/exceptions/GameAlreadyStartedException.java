package com.jingwook.mafia_server.exceptions;

public class GameAlreadyStartedException extends RuntimeException {
    public GameAlreadyStartedException(String errorString) {
        super(errorString);
    }
}
