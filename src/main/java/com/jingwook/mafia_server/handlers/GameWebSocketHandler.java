package com.jingwook.mafia_server.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jingwook.mafia_server.enums.ChatType;
import com.jingwook.mafia_server.enums.WebSocketMessageType;
import com.jingwook.mafia_server.events.ChatEvent;
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

    // "gameId-chatType" -> Sink
    private final Map<String, Sinks.Many<String>> gameChatSinks = new ConcurrentHashMap<>();
    // "gameId-chatType" -> 연결 수
    private final Map<String, AtomicInteger> connectionCounts = new ConcurrentHashMap<>();

    public GameWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String path = session.getHandshakeInfo().getUri().getPath();
        GameChatInfo chatInfo = extractGameChatInfo(path);

        if (chatInfo == null) {
            log.warn("Invalid WebSocket path: {}", path);
            return session.close();
        }

        String sinkKey = chatInfo.getSinkKey();
        Sinks.Many<String> sink = getOrCreateSink(sinkKey);
        incrementConnectionCount(sinkKey);

        Mono<Void> output = createOutputMono(session, sink);
        Mono<Void> input = createInputMono(session);

        return Mono.zip(input, output).then()
                .doFinally(signalType -> {
                    decrementConnectionCount(sinkKey);
                    cleanupSinkIfNoConnections(sinkKey);
                });
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

    private Sinks.Many<String> getOrCreateSink(String sinkKey) {
        return gameChatSinks.computeIfAbsent(
                sinkKey,
                k -> Sinks.many().multicast().onBackpressureBuffer());
    }

    private void incrementConnectionCount(String sinkKey) {
        connectionCounts.computeIfAbsent(sinkKey, k -> new AtomicInteger(0)).incrementAndGet();
    }

    private void decrementConnectionCount(String sinkKey) {
        AtomicInteger count = connectionCounts.get(sinkKey);
        if (count != null) {
            count.decrementAndGet();
        }
    }

    private void cleanupSinkIfNoConnections(String sinkKey) {
        AtomicInteger count = connectionCounts.get(sinkKey);
        if (count != null && count.get() == 0) {
            gameChatSinks.remove(sinkKey);
            connectionCounts.remove(sinkKey);
            log.debug("Cleaned up sink for: {}", sinkKey);
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

    private boolean isGameChat(ChatType chatType) {
        return chatType != ChatType.WAITING_ROOM;
    }

    private String buildSinkKey(String gameId, ChatType chatType) {
        return gameId + "-" + chatType;
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
