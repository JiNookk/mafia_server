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
@RequestMapping("/games/{gameId}/chat")
public class GameChatController {
    private final ChatService chatService;

    public GameChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/{chatType}")
    public Mono<ChatMessageDto> sendChat(
            @PathVariable String gameId,
            @PathVariable String chatType,
            @Valid @RequestBody SendChatDto dto) {
        ChatType type = parseChatType(chatType);
        return chatService.sendGameChat(gameId, dto, type);
    }

    @GetMapping("/{chatType}")
    public Mono<List<ChatMessageDto>> getChatHistory(
            @PathVariable String gameId,
            @PathVariable String chatType,
            @RequestParam String userId,
            @RequestParam(defaultValue = "50") int limit) {
        ChatType type = parseChatType(chatType);
        return chatService.getGameChatHistory(gameId, userId, type, limit);
    }

    private ChatType parseChatType(String chatType) {
        try {
            return ChatType.valueOf("GAME_" + chatType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid chat type: " + chatType +
                    ". Must be one of: all, mafia, dead");
        }
    }
}
