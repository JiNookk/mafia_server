package com.jingwook.mafia_server.entities;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Table("chat_messages")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageEntity {
    @Id
    private Long id;

    @Column("room_id")
    private String roomId;

    @Column("user_id")
    private String userId;

    @Column("chat_type")
    private String chatType;

    @Column("message")
    private String message;

    @Column("created_at")
    private LocalDateTime createdAt;
}
