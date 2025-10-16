package com.jingwook.mafia_server.dtos;

import com.jingwook.mafia_server.enums.ChatType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private Long id;
    private String contextId; // roomId 또는 gameId
    private String userId;
    private String nickname;
    private ChatType chatType;
    private String message;
    private LocalDateTime timestamp;
}
