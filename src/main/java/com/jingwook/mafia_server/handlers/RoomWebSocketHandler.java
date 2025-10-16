package com.jingwook.mafia_server.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jingwook.mafia_server.enums.WebSocketMessageType;
import com.jingwook.mafia_server.events.ChatEvent;
import com.jingwook.mafia_server.events.GameStartedEvent;
import com.jingwook.mafia_server.events.RoomUpdateEvent;
import com.jingwook.mafia_server.services.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RoomWebSocketHandler implements WebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(RoomWebSocketHandler.class);

    private final RoomService roomService;
    private final ObjectMapper objectMapper;

    // roomId -> Sink
    private final Map<String, Sinks.Many<String>> roomSinks = new ConcurrentHashMap<>();
    // roomId -> 연결 수 (Sink의 currentSubscriberCount()는 정확하지 않을 수 있으므로)
    private final Map<String, AtomicInteger> connectionCounts = new ConcurrentHashMap<>();

    public RoomWebSocketHandler(RoomService roomService, ObjectMapper objectMapper) {
        this.roomService = roomService;
        this.objectMapper = objectMapper;
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
        return session.send(sink.asFlux().map(session::textMessage));
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
                .map(msg -> msg.getPayloadAsText())
                .doOnNext(message -> {
                    // 필요시 클라이언트 메시지 처리 (ping/pong 등)
                    log.debug("Received message: {}", message);
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

        broadcastToRoom(roomId, WebSocketMessageType.ROOM_UPDATE, event.getRoomDetail());
    }

    @EventListener
    @Async
    public void handleChatEvent(ChatEvent event) {
        String roomId = event.getRoomId();
        log.info("WebSocketHandler: Received chat event for roomId: {}", roomId);

        broadcastToRoom(roomId, WebSocketMessageType.CHAT, event.getChatMessage());
    }

    @EventListener
    @Async
    public void handleGameStartedEvent(GameStartedEvent event) {
        String roomId = event.getRoomId();
        log.info("WebSocketHandler: Received game started event for roomId: {}", roomId);

        broadcastToRoom(roomId, WebSocketMessageType.GAME_STARTED, Map.of("gameId", event.getGameId()));
    }

    private void broadcastToRoom(String roomId, WebSocketMessageType type, Object data) {
        Sinks.Many<String> sink = roomSinks.get(roomId);

        if (sink == null) {
            log.warn("WebSocketHandler: No sink found for roomId: {}", roomId);
            return;
        }

        log.info("WebSocketHandler: Found sink for roomId: {}", roomId);
        serializeAndEmit(roomId, type, data, sink);
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
