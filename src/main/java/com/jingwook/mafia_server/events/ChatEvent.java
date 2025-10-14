package com.jingwook.mafia_server.events;

import com.jingwook.mafia_server.dtos.ChatMessageDto;
import lombok.Getter;

@Getter
public class ChatEvent {
    private final String roomId;
    private final ChatMessageDto chatMessage;

    public ChatEvent(String roomId, ChatMessageDto chatMessage) {
        this.roomId = roomId;
        this.chatMessage = chatMessage;
    }
}
