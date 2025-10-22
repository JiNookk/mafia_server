package com.jingwook.mafia_server.repositories;

import com.jingwook.mafia_server.entities.ChatMessageEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface ChatMessageRepository extends R2dbcRepository<ChatMessageEntity, Long> {

    @Query("SELECT * FROM (" +
           "  SELECT * FROM chat_messages " +
           "  WHERE context_id = :contextId AND chat_type = :chatType " +
           "  ORDER BY created_at DESC LIMIT :limit" +
           ") AS recent " +
           "ORDER BY created_at ASC")
    Flux<ChatMessageEntity> findByContextIdAndChatType(String contextId, String chatType, int limit);

    @Query("SELECT * FROM (" +
           "  SELECT * FROM chat_messages " +
           "  WHERE context_id = :contextId " +
           "  ORDER BY created_at DESC LIMIT :limit" +
           ") AS recent " +
           "ORDER BY created_at ASC")
    Flux<ChatMessageEntity> findByContextIdOrderByCreatedAtDesc(String contextId, int limit);
}
