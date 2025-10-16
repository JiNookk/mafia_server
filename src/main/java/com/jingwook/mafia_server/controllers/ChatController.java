package com.jingwook.mafia_server.controllers;

import com.jingwook.mafia_server.dtos.ChatMessageDto;
import com.jingwook.mafia_server.dtos.SendChatDto;
import com.jingwook.mafia_server.enums.ChatType;
import com.jingwook.mafia_server.services.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/rooms/{roomId}/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public Mono<ChatMessageDto> sendMessage(
            @PathVariable String roomId,
            @Valid @RequestBody SendChatDto dto) {
        return chatService.sendMessage(roomId, dto);
    }

    @GetMapping
    public Mono<List<ChatMessageDto>> getChatHistory(
            @PathVariable String roomId,
            @RequestParam String userId,
            @RequestParam(defaultValue = "50") int limit) {
        // 대기실 채팅만 조회
        return chatService.getChatHistory(roomId, userId, ChatType.WAITING_ROOM, limit);
    }
}
