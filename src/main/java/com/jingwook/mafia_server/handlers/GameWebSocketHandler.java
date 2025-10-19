package com.jingwook.mafia_server.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jingwook.mafia_server.enums.ChatType;
import com.jingwook.mafia_server.enums.WebSocketMessageType;
import com.jingwook.mafia_server.events.ChatEvent;
import com.jingwook.mafia_server.events.GameEndedEvent;
import com.jingwook.mafia_server.events.PhaseChangedEvent;
import com.jingwook.mafia_server.events.PlayerDiedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GameWebSocketHandler implements WebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final ObjectMapper objectMapper;

    // "gameId-chatType" -> Sink (채팅용)
    private final Map<String, Sinks.Many<String>> gameChatSinks = new ConcurrentHashMap<>();
    // "gameId-chatType" -> 연결 수 (채팅용)
    private final Map<String, AtomicInteger> chatConnectionCounts = new ConcurrentHashMap<>();

    // gameId -> Sink (게임 이벤트용)
    private final Map<String, Sinks.Many<String>> gameEventSinks = new ConcurrentHashMap<>();
    // gameId -> 연결 수 (게임 이벤트용)
    private final Map<String, AtomicInteger> eventConnectionCounts = new ConcurrentHashMap<>();

    public GameWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String path = session.getHandshakeInfo().getUri().getPath();

        // /ws/games/{gameId}/events 형태인지 확인
        if (isGameEventPath(path)) {
            return handleGameEventConnection(session, path);
        }

        // 기존 게임 채팅 처리
        GameChatInfo chatInfo = extractGameChatInfo(path);
        if (chatInfo == null) {
            log.warn("Invalid WebSocket path: {}", path);
            return session.close();
        }

        return handleGameChatConnection(session, chatInfo);
    }

    private boolean isGameEventPath(String path) {
        return path.matches("/ws/games/[^/]+/events");
    }

    private Mono<Void> handleGameEventConnection(WebSocketSession session, String path) {
        String gameId = extractGameIdFromEventPath(path);
        if (gameId == null) {
            log.warn("Invalid game event path: {}", path);
            return session.close();
        }

        Sinks.Many<String> sink = getOrCreateEventSink(gameId);
        incrementEventConnectionCount(gameId);

        Mono<Void> output = createOutputMono(session, sink);
        Mono<Void> input = createInputMono(session);

        return Mono.zip(input, output).then()
                .doFinally(signalType -> {
                    decrementEventConnectionCount(gameId);
                    cleanupEventSinkIfNoConnections(gameId);
                });
    }

    private Mono<Void> handleGameChatConnection(WebSocketSession session, GameChatInfo chatInfo) {
        String sinkKey = chatInfo.getSinkKey();
        Sinks.Many<String> sink = getOrCreateChatSink(sinkKey);
        incrementChatConnectionCount(sinkKey);

        Mono<Void> output = createOutputMono(session, sink);
        Mono<Void> input = createInputMono(session);

        return Mono.zip(input, output).then()
                .doFinally(signalType -> {
                    decrementChatConnectionCount(sinkKey);
                    cleanupChatSinkIfNoConnections(sinkKey);
                });
    }

    private String extractGameIdFromEventPath(String path) {
        // /ws/games/{gameId}/events
        String[] parts = path.split("/");
        if (parts.length >= 4) {
            return parts[3];
        }
        return null;
    }

    private GameChatInfo extractGameChatInfo(String path) {
        // /ws/games/{gameId}/all, /ws/games/{gameId}/mafia, /ws/games/{gameId}/dead
        String[] parts = path.split("/");

        if (!isValidGameChatPath(parts)) {
            return null;
        }

        String gameId = parts[3];
        ChatType chatType = parseChatType(parts[4]);

        return chatType != null ? new GameChatInfo(gameId, chatType) : null;
    }

    private boolean isValidGameChatPath(String[] parts) {
        return parts.length >= 5 && "ws".equals(parts[1]) && "games".equals(parts[2]);
    }

    private ChatType parseChatType(String chatTypeStr) {
        try {
            return ChatType.valueOf("GAME_" + chatTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // 게임 이벤트 Sink 관리
    private Sinks.Many<String> getOrCreateEventSink(String gameId) {
        return gameEventSinks.computeIfAbsent(
                gameId,
                k -> Sinks.many().multicast().onBackpressureBuffer());
    }

    private void incrementEventConnectionCount(String gameId) {
        eventConnectionCounts.computeIfAbsent(gameId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    private void decrementEventConnectionCount(String gameId) {
        AtomicInteger count = eventConnectionCounts.get(gameId);
        if (count != null) {
            count.decrementAndGet();
        }
    }

    private void cleanupEventSinkIfNoConnections(String gameId) {
        AtomicInteger count = eventConnectionCounts.get(gameId);
        if (count != null && count.get() == 0) {
            gameEventSinks.remove(gameId);
            eventConnectionCounts.remove(gameId);
            log.debug("Cleaned up event sink for game: {}", gameId);
        }
    }

    // 게임 채팅 Sink 관리
    private Sinks.Many<String> getOrCreateChatSink(String sinkKey) {
        return gameChatSinks.computeIfAbsent(
                sinkKey,
                k -> Sinks.many().multicast().onBackpressureBuffer());
    }

    private void incrementChatConnectionCount(String sinkKey) {
        chatConnectionCounts.computeIfAbsent(sinkKey, k -> new AtomicInteger(0)).incrementAndGet();
    }

    private void decrementChatConnectionCount(String sinkKey) {
        AtomicInteger count = chatConnectionCounts.get(sinkKey);
        if (count != null) {
            count.decrementAndGet();
        }
    }

    private void cleanupChatSinkIfNoConnections(String sinkKey) {
        AtomicInteger count = chatConnectionCounts.get(sinkKey);
        if (count != null && count.get() == 0) {
            gameChatSinks.remove(sinkKey);
            chatConnectionCounts.remove(sinkKey);
            log.debug("Cleaned up chat sink for: {}", sinkKey);
        }
    }

    private Mono<Void> createOutputMono(WebSocketSession session, Sinks.Many<String> sink) {
        return session.send(sink.asFlux().map(session::textMessage));
    }

    private Mono<Void> createInputMono(WebSocketSession session) {
        return session.receive()
                .map(msg -> msg.getPayloadAsText())
                .doOnNext(message -> {
                    log.debug("Received message: {}", message);
                })
                .then();
    }

    @EventListener
    @Async
    public void handleChatEvent(ChatEvent event) {
        ChatType chatType = event.getChatMessage().getChatType();

        if (!isGameChat(chatType)) {
            return;
        }

        // ChatEvent.contextId는 게임 채팅의 경우 gameId
        String gameId = event.getContextId();
        if (gameId == null) {
            log.warn("GameWebSocketHandler: contextId is null for game chat");
            return;
        }

        String sinkKey = buildSinkKey(gameId, chatType);
        log.info("GameWebSocketHandler: Received chat event for sinkKey: {}", sinkKey);
        broadcastToGameChat(sinkKey, event.getChatMessage());
    }

    @EventListener
    @Async
    public void handlePhaseChangedEvent(PhaseChangedEvent event) {
        String gameId = event.getGameId();
        log.info("GameWebSocketHandler: Received phase changed event for gameId: {}", gameId);

        broadcastToGameEvent(gameId, WebSocketMessageType.PHASE_CHANGED, event.getPhaseData());
    }

    @EventListener
    @Async
    public void handlePlayerDiedEvent(PlayerDiedEvent event) {
        String gameId = event.getGameId();
        log.info("GameWebSocketHandler: Received player died event for gameId: {}", gameId);

        broadcastToGameEvent(gameId, WebSocketMessageType.PLAYER_DIED, Map.of(
                "gameId", event.getGameId(),
                "deadPlayerIds", event.getDeadPlayerIds(),
                "reason", event.getReason()
        ));
    }

    @EventListener
    @Async
    public void handleGameEndedEvent(GameEndedEvent event) {
        String gameId = event.getGameId();
        log.info("GameWebSocketHandler: Received game ended event for gameId: {}", gameId);

        broadcastToGameEvent(gameId, WebSocketMessageType.GAME_ENDED, Map.of(
                "gameId", event.getGameId(),
                "winnerTeam", event.getWinnerTeam()
        ));
    }

    private boolean isGameChat(ChatType chatType) {
        return chatType != ChatType.WAITING_ROOM;
    }

    private String buildSinkKey(String gameId, ChatType chatType) {
        return gameId + "-" + chatType;
    }

    private void broadcastToGameEvent(String gameId, WebSocketMessageType type, Object data) {
        Sinks.Many<String> sink = gameEventSinks.get(gameId);

        if (sink == null) {
            log.warn("GameWebSocketHandler: No event sink found for gameId: {}", gameId);
            return;
        }

        log.info("GameWebSocketHandler: Found event sink for gameId: {}", gameId);
        serializeAndEmitGameEvent(gameId, type, data, sink);
    }

    private void serializeAndEmitGameEvent(String gameId, WebSocketMessageType type, Object data, Sinks.Many<String> sink) {
        try {
            String json = serializeMessage(type, data);
            emitGameEventMessage(gameId, type, json, sink);
        } catch (Exception e) {
            log.error("Failed to serialize {} for game: {}", type, gameId, e);
        }
    }

    private void emitGameEventMessage(String gameId, WebSocketMessageType type, String json, Sinks.Many<String> sink) {
        Sinks.EmitResult result = sink.tryEmitNext(json);

        if (result.isFailure()) {
            log.warn("Failed to emit {} for game {}: {}", type, gameId, result);
        } else {
            log.info("GameWebSocketHandler: Successfully emitted {} for gameId: {}", type, gameId);
        }
    }

    private void broadcastToGameChat(String sinkKey, Object data) {
        Sinks.Many<String> sink = gameChatSinks.get(sinkKey);

        if (sink == null) {
            log.warn("GameWebSocketHandler: No sink found for sinkKey: {}", sinkKey);
            return;
        }

        log.info("GameWebSocketHandler: Found sink for sinkKey: {}", sinkKey);
        serializeAndEmitGameChat(sinkKey, data, sink);
    }

    private void serializeAndEmitGameChat(String sinkKey, Object data, Sinks.Many<String> sink) {
        try {
            String json = serializeChatMessage(data);
            emitChatMessage(sinkKey, json, sink);
        } catch (Exception e) {
            log.error("Failed to serialize CHAT for sinkKey: {}", sinkKey, e);
        }
    }

    private String serializeMessage(WebSocketMessageType type, Object data) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "type", type.name(),
                "data", data
        ));
    }

    private String serializeChatMessage(Object data) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "type", WebSocketMessageType.CHAT.name(),
                "data", data
        ));
    }

    private void emitChatMessage(String sinkKey, String json, Sinks.Many<String> sink) {
        Sinks.EmitResult result = sink.tryEmitNext(json);

        if (result.isFailure()) {
            log.warn("Failed to emit CHAT for sinkKey {}: {}", sinkKey, result);
        } else {
            log.info("GameWebSocketHandler: Successfully emitted CHAT for sinkKey: {}", sinkKey);
        }
    }

    private static class GameChatInfo {
        private final String gameId;
        private final ChatType chatType;

        public GameChatInfo(String gameId, ChatType chatType) {
            this.gameId = gameId;
            this.chatType = chatType;
        }

        public String getSinkKey() {
            return gameId + "-" + chatType;
        }

        public String getGameId() {
            return gameId;
        }

        public ChatType getChatType() {
            return chatType;
        }
    }
}
