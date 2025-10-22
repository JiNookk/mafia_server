package com.jingwook.mafia_server.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jingwook.mafia_server.enums.WebSocketMessageType;
import com.jingwook.mafia_server.events.ChatEvent;
import com.jingwook.mafia_server.events.GameStartedEvent;
import com.jingwook.mafia_server.events.RoomUpdateEvent;
import com.jingwook.mafia_server.services.RedisMessageService;
import com.jingwook.mafia_server.services.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RoomWebSocketHandler implements WebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(RoomWebSocketHandler.class);

    private final RoomService roomService;
    private final ObjectMapper objectMapper;
    private final RedisMessageService redisMessageService;

    // roomId -> Sink
    private final Map<String, Sinks.Many<String>> roomSinks = new ConcurrentHashMap<>();
    // roomId -> 연결 수 (Sink의 currentSubscriberCount()는 정확하지 않을 수 있으므로)
    private final Map<String, AtomicInteger> connectionCounts = new ConcurrentHashMap<>();

    public RoomWebSocketHandler(
            RoomService roomService,
            ObjectMapper objectMapper,
            RedisMessageService redisMessageService) {
        this.roomService = roomService;
        this.objectMapper = objectMapper;
        this.redisMessageService = redisMessageService;
    }

    /**
     * Redis Pub/Sub 구독 시작
     * 다른 서버 인스턴스에서 발행한 메시지를 받아서 로컬 Sink로 전달
     */
    @PostConstruct
    public void subscribeToRedis() {
        redisMessageService.subscribeToRoomUpdates()
                .doOnNext(message -> {
                    if (message == null) {
                        return;
                    }
                    String roomId = message.getKey();
                    log.info("Received Redis message for roomId: {}", roomId);

                    Sinks.Many<String> sink = roomSinks.get(roomId);
                    if (sink != null) {
                        try {
                            String json = objectMapper.writeValueAsString(Map.of(
                                    "type", message.getType().name(),
                                    "data", message.getData()
                            ));
                            sink.tryEmitNext(json);
                        } catch (Exception e) {
                            log.error("Failed to emit Redis message to sink", e);
                        }
                    }
                })
                .onErrorContinue((error, obj) -> log.error("Error in Redis subscription, continuing", error))
                .subscribe();
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String roomId = extractRoomId(session.getHandshakeInfo().getUri().getPath());

        if (roomId == null) {
            log.warn("Invalid WebSocket path: {}", session.getHandshakeInfo().getUri().getPath());
            return session.close();
        }

        Sinks.Many<String> sink = getOrCreateRoomSink(roomId);
        incrementConnectionCount(roomId);

        Mono<Void> output = createOutputMono(session, sink);
        Mono<Void> sendInitialData = sendInitialRoomData(roomId, sink);
        Mono<Void> input = createInputMono(session);

        return sendInitialData
                .then(Mono.zip(input, output).then())
                .doFinally(signalType -> {
                    decrementConnectionCount(roomId);
                    cleanupSinkIfNoConnections(roomId);
                });
    }

    private Sinks.Many<String> getOrCreateRoomSink(String roomId) {
        return roomSinks.computeIfAbsent(
                roomId,
                k -> Sinks.many().multicast().onBackpressureBuffer());
    }

    private void incrementConnectionCount(String roomId) {
        connectionCounts.computeIfAbsent(roomId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    private void decrementConnectionCount(String roomId) {
        AtomicInteger count = connectionCounts.get(roomId);
        if (count != null) {
            count.decrementAndGet();
        }
    }

    private void cleanupSinkIfNoConnections(String roomId) {
        AtomicInteger count = connectionCounts.get(roomId);
        if (count != null && count.get() == 0) {
            roomSinks.remove(roomId);
            connectionCounts.remove(roomId);
            log.debug("Cleaned up sink for room: {}", roomId);
        }
    }

    private Mono<Void> createOutputMono(WebSocketSession session, Sinks.Many<String> sink) {
        return session.send(
            sink.asFlux()
                .map(session::textMessage)
                .doOnError(e -> log.error("Error sending WebSocket message", e))
        );
    }

    private Mono<Void> sendInitialRoomData(String roomId, Sinks.Many<String> sink) {
        return roomService.getDetail(roomId)
                .flatMap(roomDetail -> {
                    try {
                        String json = objectMapper.writeValueAsString(Map.of(
                                "type", WebSocketMessageType.ROOM_UPDATE.name(),
                                "data", roomDetail
                        ));
                        sink.tryEmitNext(json);
                        return Mono.empty();
                    } catch (Exception e) {
                        log.error("Failed to serialize initial room data for room: {}", roomId, e);
                        return Mono.error(e);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to load initial room data for room: {}", roomId, e);
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> createInputMono(WebSocketSession session) {
        return session.receive()
                .doOnNext(msg -> {
                    try {
                        String text = msg.getPayloadAsText();
                        // 필요시 클라이언트 메시지 처리 (ping/pong 등)
                        log.debug("Received message: {}", text);
                    } finally {
                        // DataBuffer 명시적 해제
                        msg.release();
                    }
                })
                .then();
    }

    private String extractRoomId(String path) {
        // /ws/rooms/{roomId} 형태에서 roomId 추출
        String[] parts = path.split("/");
        if (parts.length >= 4 && "rooms".equals(parts[2])) {
            return parts[3];
        }
        return null;
    }

    @EventListener
    @Async
    public void handleRoomUpdate(RoomUpdateEvent event) {
        String roomId = event.getRoomId();
        log.info("WebSocketHandler: Received room update event for roomId: {}", roomId);

        // 1. 로컬 Sink에 직접 전달
        broadcastToRoomLocal(roomId, WebSocketMessageType.ROOM_UPDATE, event.getRoomDetail());

        // 2. Redis로 발행 (다른 서버로 전파)
        redisMessageService.publishRoomUpdate(roomId, event.getRoomDetail())
                .doOnSuccess(count -> log.info("Published room update to Redis for roomId: {}", roomId))
                .subscribe();
    }

    @EventListener
    @Async
    public void handleChatEvent(ChatEvent event) {
        // ChatEvent.contextId는 대기실 채팅의 경우 roomId
        String roomId = event.getContextId();
        log.info("WebSocketHandler: Received chat event for roomId: {}", roomId);

        // 1. 로컬 Sink에 직접 전달
        broadcastToRoomLocal(roomId, WebSocketMessageType.CHAT, event.getChatMessage());

        // 2. Redis로 발행
        redisMessageService.publishRoomUpdate(roomId, event.getChatMessage())
                .doOnSuccess(count -> log.info("Published chat to Redis for roomId: {}", roomId))
                .subscribe();
    }

    @EventListener
    @Async
    public void handleGameStartedEvent(GameStartedEvent event) {
        String roomId = event.getRoomId();
        log.info("WebSocketHandler: Received game started event for roomId: {}", roomId);

        Map<String, Object> data = Map.of("gameId", event.getGameId());

        // 1. 로컬 Sink에 직접 전달
        broadcastToRoomLocal(roomId, WebSocketMessageType.GAME_STARTED, data);

        // 2. Redis로 발행
        redisMessageService.publishRoomUpdate(roomId, data)
                .doOnSuccess(count -> log.info("Published game started to Redis for roomId: {}", roomId))
                .subscribe();
    }

    /**
     * 로컬 Sink로 직접 방 메시지 브로드캐스트
     */
    private void broadcastToRoomLocal(String roomId, WebSocketMessageType type, Object data) {
        Sinks.Many<String> sink = roomSinks.get(roomId);

        if (sink == null) {
            log.debug("No local sink found for roomId: {} (no clients connected)", roomId);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", type.name(),
                    "data", data
            ));
            Sinks.EmitResult result = sink.tryEmitNext(json);

            if (result.isFailure()) {
                log.warn("Failed to emit {} to local sink for room {}: {}", type, roomId, result);
            } else {
                log.info("Successfully emitted {} to local sink for roomId: {}", type, roomId);
            }
        } catch (Exception e) {
            log.error("Failed to broadcast {} to local sink", type, e);
        }
    }

    private void serializeAndEmit(String roomId, WebSocketMessageType type, Object data, Sinks.Many<String> sink) {
        try {
            String json = serializeMessage(type, data);
            emitMessage(roomId, type, json, sink);
        } catch (Exception e) {
            log.error("Failed to serialize {} for room: {}", type, roomId, e);
        }
    }

    private String serializeMessage(WebSocketMessageType type, Object data) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "type", type.name(),
                "data", data
        ));
    }

    private void emitMessage(String roomId, WebSocketMessageType type, String json, Sinks.Many<String> sink) {
        Sinks.EmitResult result = sink.tryEmitNext(json);

        if (result.isFailure()) {
            log.warn("Failed to emit {} for room {}: {}", type, roomId, result);
        } else {
            log.info("WebSocketHandler: Successfully emitted {} for roomId: {}", type, roomId);
        }
    }
}
