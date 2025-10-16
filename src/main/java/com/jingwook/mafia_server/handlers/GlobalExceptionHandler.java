package com.jingwook.mafia_server.handlers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.jingwook.mafia_server.dtos.ErrorResponse;
import com.jingwook.mafia_server.exceptions.GameAlreadyStartedException;
import com.jingwook.mafia_server.exceptions.RoomFullException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RoomFullException.class)
    public ResponseEntity<ErrorResponse> handleRoomFullException(RoomFullException ex) {
        ErrorResponse errorResponse = new ErrorResponse("ROOM_FULL", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(GameAlreadyStartedException.class)
    public ResponseEntity<ErrorResponse> handleGameAlreadyStartedException(GameAlreadyStartedException ex) {
        ErrorResponse errorResponse = new ErrorResponse("GAME_ALREADY_STARTED", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
}
