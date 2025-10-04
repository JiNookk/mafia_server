package com.jingwook.mafia_server.exceptions;

public class UserAlreadyExistException extends RuntimeException{
    public UserAlreadyExistException(String errorString) {
        super(errorString);
    }
}
