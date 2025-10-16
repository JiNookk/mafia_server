package com.jingwook.mafia_server.services;

import com.jingwook.mafia_server.dtos.ChatMessageDto;
import com.jingwook.mafia_server.dtos.SendChatDto;
import com.jingwook.mafia_server.entities.ChatMessageEntity;
import com.jingwook.mafia_server.entities.GamePlayerEntity;
import com.jingwook.mafia_server.enums.ChatType;
import com.jingwook.mafia_server.enums.PlayerRole;
import com.jingwook.mafia_server.events.ChatEvent;
import com.jingwook.mafia_server.repositories.ChatMessageRepository;
import com.jingwook.mafia_server.repositories.GamePlayerR2dbcRepository;
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
    private final GamePlayerR2dbcRepository gamePlayerRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ChatService(ChatMessageRepository chatMessageRepository,
                       RoomMemberR2dbcRepository roomMemberRepository,
                       GamePlayerR2dbcRepository gamePlayerRepository,
                       UserRepository userRepository,
                       ApplicationEventPublisher eventPublisher) {
        this.chatMessageRepository = chatMessageRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Mono<ChatMessageDto> sendMessage(String roomId, SendChatDto dto) {
        log.info("Sending waiting room chat message: roomId={}, userId={}",
                 roomId, dto.getUserId());

        // 대기실 채팅은 방 멤버인지만 확인
        return validateRoomMembership(roomId, dto.getUserId())
                .then(saveWaitingRoomChatMessage(roomId, dto))
                .flatMap(entity -> buildChatMessageDto(entity))
                .doOnSuccess(chatDto -> {
                    log.info("Chat message saved, publishing event: messageId={}", chatDto.getId());
                    eventPublisher.publishEvent(new ChatEvent(roomId, chatDto));
                });
    }

    private Mono<Void> validateRoomMembership(String roomId, String userId) {
        return roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Not a member of this room")))
                .then();
    }

    private Mono<ChatMessageEntity> saveWaitingRoomChatMessage(String roomId, SendChatDto dto) {
        ChatMessageEntity entity = ChatMessageEntity.builder()
                .contextId(roomId)
                .userId(dto.getUserId())
                .chatType(ChatType.WAITING_ROOM.toString())
                .message(dto.getMessage())
                .createdAt(LocalDateTime.now())
                .build();

        return chatMessageRepository.save(entity);
    }

    private Mono<ChatMessageDto> buildChatMessageDto(ChatMessageEntity entity) {
        return userRepository.findById(entity.getUserId())
                .map(user -> ChatMessageDto.builder()
                        .id(entity.getId())
                        .contextId(entity.getContextId())
                        .userId(entity.getUserId())
                        .nickname(user.getNickname())
                        .chatType(ChatType.valueOf(entity.getChatType()))
                        .message(entity.getMessage())
                        .timestamp(entity.getCreatedAt())
                        .build());
    }

    public Mono<List<ChatMessageDto>> getChatHistory(String roomId, String userId,
                                                      ChatType chatType, int limit) {
        log.info("Fetching waiting room chat history: roomId={}, userId={}, limit={}",
                 roomId, userId, limit);

        // 대기실 채팅은 방 멤버인지만 확인
        return validateRoomMembership(roomId, userId)
                .then(chatMessageRepository.findByContextIdAndChatType(roomId, chatType.toString(), limit)
                        .flatMap(this::buildChatMessageDto)
                        .collectList());
    }

    // 게임 채팅 전송
    @Transactional
    public Mono<ChatMessageDto> sendGameChat(String gameId, SendChatDto dto, ChatType chatType) {
        log.info("Sending game chat message: gameId={}, userId={}, chatType={}",
                gameId, dto.getUserId(), chatType);

        return validateGameChatPermission(gameId, dto.getUserId(), chatType)
                .then(saveGameChatMessage(gameId, dto, chatType))
                .flatMap(this::buildChatMessageDto)
                .doOnSuccess(chatDto -> {
                    log.info("Game chat message saved, publishing event: messageId={}", chatDto.getId());
                    eventPublisher.publishEvent(new ChatEvent(gameId, chatDto));
                });
    }

    private Mono<Void> validateGameChatPermission(String gameId, String userId, ChatType chatType) {
        return findGamePlayer(gameId, userId)
                .flatMap(player -> validateGamePermissionByType(player, chatType));
    }

    private Mono<GamePlayerEntity> findGamePlayer(String gameId, String userId) {
        return gamePlayerRepository.findByGameIdAndUserId(gameId, userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Not a player in this game")));
    }

    private Mono<Void> validateGamePermissionByType(GamePlayerEntity player, ChatType chatType) {
        switch (chatType) {
            case GAME_MAFIA:
                return validateGameMafiaPermission(player);
            case GAME_DEAD:
                return validateGameDeadPermission(player);
            case GAME_ALL:
                return validateGameAllChatPermission(player);
            default:
                return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid chat type for game"));
        }
    }

    private Mono<Void> validateGameMafiaPermission(GamePlayerEntity player) {
        PlayerRole role = player.getRoleAsEnum();
        if (!PlayerRole.MAFIA.equals(role)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only mafia can send mafia chat"));
        }
        return Mono.empty();
    }

    private Mono<Void> validateGameDeadPermission(GamePlayerEntity player) {
        if (player.getIsAlive()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only dead players can send dead chat"));
        }
        return Mono.empty();
    }

    private Mono<Void> validateGameAllChatPermission(GamePlayerEntity player) {
        if (!player.getIsAlive()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Dead players cannot send all chat"));
        }
        return Mono.empty();
    }

    private Mono<ChatMessageEntity> saveGameChatMessage(String gameId, SendChatDto dto, ChatType chatType) {
        ChatMessageEntity entity = ChatMessageEntity.builder()
                .contextId(gameId)
                .userId(dto.getUserId())
                .chatType(chatType.toString())
                .message(dto.getMessage())
                .createdAt(LocalDateTime.now())
                .build();

        return chatMessageRepository.save(entity);
    }

    // 게임 채팅 히스토리 조회
    public Mono<List<ChatMessageDto>> getGameChatHistory(String gameId, String userId,
                                                          ChatType chatType, int limit) {
        log.info("Fetching game chat history: gameId={}, userId={}, chatType={}, limit={}",
                gameId, userId, chatType, limit);

        return validateGameChatPermission(gameId, userId, chatType)
                .then(chatMessageRepository.findByContextIdAndChatType(gameId, chatType.toString(), limit)
                        .flatMap(this::buildChatMessageDto)
                        .collectList());
    }
}
