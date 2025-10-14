package com.jingwook.mafia_server.services;

import com.jingwook.mafia_server.dtos.ChatMessageDto;
import com.jingwook.mafia_server.dtos.SendChatDto;
import com.jingwook.mafia_server.entities.ChatMessageEntity;
import com.jingwook.mafia_server.enums.ChatType;
import com.jingwook.mafia_server.enums.GameRole;
import com.jingwook.mafia_server.events.ChatEvent;
import com.jingwook.mafia_server.repositories.ChatMessageRepository;
import com.jingwook.mafia_server.repositories.RoomMemberR2dbcRepository;
import com.jingwook.mafia_server.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatMessageRepository chatMessageRepository;
    private final RoomMemberR2dbcRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ChatService(ChatMessageRepository chatMessageRepository,
                       RoomMemberR2dbcRepository roomMemberRepository,
                       UserRepository userRepository,
                       ApplicationEventPublisher eventPublisher) {
        this.chatMessageRepository = chatMessageRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Mono<ChatMessageDto> sendMessage(String roomId, SendChatDto dto) {
        log.info("Sending chat message: roomId={}, userId={}, chatType={}",
                 roomId, dto.getUserId(), dto.getChatType());

        return validateChatPermission(roomId, dto.getUserId(), dto.getChatType())
                .then(saveChatMessage(roomId, dto))
                .flatMap(entity -> buildChatMessageDto(entity))
                .doOnSuccess(chatDto -> {
                    log.info("Chat message saved, publishing event: messageId={}", chatDto.getId());
                    eventPublisher.publishEvent(new ChatEvent(roomId, chatDto));
                });
    }

    private Mono<Void> validateChatPermission(String roomId, String userId, ChatType chatType) {
        return findRoomMember(roomId, userId)
                .flatMap(member -> validatePermissionByType(member, chatType, "send"));
    }

    private Mono<com.jingwook.mafia_server.entities.RoomMemberEntity> findRoomMember(String roomId, String userId) {
        return roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Not a member of this room")));
    }

    private Mono<Void> validatePermissionByType(
            com.jingwook.mafia_server.entities.RoomMemberEntity member,
            ChatType chatType,
            String action) {
        switch (chatType) {
            case MAFIA:
                return validateMafiaPermission(member, action);
            case DEAD:
                return validateDeadPermission(member, action);
            case ALL:
                return validateAllChatPermission(member, action);
            default:
                return Mono.empty();
        }
    }

    private Mono<Void> validateMafiaPermission(
            com.jingwook.mafia_server.entities.RoomMemberEntity member,
            String action) {
        GameRole gameRole = member.getGameRoleAsEnum();
        if (gameRole == null || !GameRole.MAFIA.equals(gameRole)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only mafia can " + action + " mafia chat"));
        }
        return Mono.empty();
    }

    private Mono<Void> validateDeadPermission(
            com.jingwook.mafia_server.entities.RoomMemberEntity member,
            String action) {
        Boolean isAlive = member.getIsAlive();
        // 게임 시작 전(null) 또는 살아있으면(true) 죽은 사람 채팅 불가
        if (isAlive == null || isAlive) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only dead players can " + action + " dead chat"));
        }
        return Mono.empty();
    }

    private Mono<Void> validateAllChatPermission(
            com.jingwook.mafia_server.entities.RoomMemberEntity member,
            String action) {
        Boolean isAlive = member.getIsAlive();
        // send이고 게임 중(not null)이고 죽었으면(false) 전체 채팅 불가
        if ("send".equals(action) && isAlive != null && !isAlive) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Dead players cannot send all chat"));
        }
        // 게임 시작 전(null) 또는 살아있으면(true) OK
        return Mono.empty();
    }

    private Mono<ChatMessageEntity> saveChatMessage(String roomId, SendChatDto dto) {
        ChatMessageEntity entity = ChatMessageEntity.builder()
                .roomId(roomId)
                .userId(dto.getUserId())
                .chatType(dto.getChatType().toString())
                .message(dto.getMessage())
                .createdAt(LocalDateTime.now())
                .build();

        return chatMessageRepository.save(entity);
    }

    private Mono<ChatMessageDto> buildChatMessageDto(ChatMessageEntity entity) {
        return userRepository.findById(entity.getUserId())
                .map(user -> ChatMessageDto.builder()
                        .id(entity.getId())
                        .roomId(entity.getRoomId())
                        .userId(entity.getUserId())
                        .nickname(user.getNickname())
                        .chatType(ChatType.valueOf(entity.getChatType()))
                        .message(entity.getMessage())
                        .timestamp(entity.getCreatedAt())
                        .build());
    }

    public Mono<List<ChatMessageDto>> getChatHistory(String roomId, String userId,
                                                      ChatType chatType, int limit) {
        log.info("Fetching chat history: roomId={}, userId={}, chatType={}, limit={}",
                 roomId, userId, chatType, limit);

        return canReceiveChat(roomId, userId, chatType)
                .then(chatMessageRepository.findByRoomIdAndChatType(roomId, chatType.toString(), limit)
                        .flatMap(this::buildChatMessageDto)
                        .collectList());
    }

    private Mono<Void> canReceiveChat(String roomId, String userId, ChatType chatType) {
        return findRoomMember(roomId, userId)
                .flatMap(member -> validatePermissionByType(member, chatType, "view"));
    }
}
