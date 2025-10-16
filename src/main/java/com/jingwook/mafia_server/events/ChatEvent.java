package com.jingwook.mafia_server.events;

import com.jingwook.mafia_server.dtos.ChatMessageDto;
import lombok.Getter;

@Getter
public class ChatEvent {
    private final String contextId; // roomId 또는 gameId
    private final ChatMessageDto chatMessage;

    public ChatEvent(String contextId, ChatMessageDto chatMessage) {
        this.contextId = contextId;
        this.chatMessage = chatMessage;
    }
}
