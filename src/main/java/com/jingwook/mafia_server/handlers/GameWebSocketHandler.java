package com.jingwook.mafia_server.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jingwook.mafia_server.enums.ChatType;
import com.jingwook.mafia_server.enums.WebSocketMessageType;
import com.jingwook.mafia_server.events.ChatEvent;
import com.jingwook.mafia_server.events.GameEndedEvent;
import com.jingwook.mafia_server.events.PhaseChangedEvent;
import com.jingwook.mafia_server.events.PlayerDiedEvent;
import com.jingwook.mafia_server.services.RedisMessageService;
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
public class GameWebSocketHandler implements WebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final RedisMessageService redisMessageService;

    // "gameId-chatType" -> Sink (채팅용)
    private final Map<String, Sinks.Many<String>> gameChatSinks = new ConcurrentHashMap<>();
    // "gameId-chatType" -> 연결 수 (채팅용)
    private final Map<String, AtomicInteger> chatConnectionCounts = new ConcurrentHashMap<>();

    // gameId -> Sink (게임 이벤트용)
    private final Map<String, Sinks.Many<String>> gameEventSinks = new ConcurrentHashMap<>();
    // gameId -> 연결 수 (게임 이벤트용)
    private final Map<String, AtomicInteger> eventConnectionCounts = new ConcurrentHashMap<>();

    public GameWebSocketHandler(ObjectMapper objectMapper, RedisMessageService redisMessageService) {
        this.objectMapper = objectMapper;
        this.redisMessageService = redisMessageService;
    }

    /**
     * Redis Pub/Sub 구독 시작
     */
    @PostConstruct
    public void subscribeToRedis() {
        // 게임 채팅 구독
        redisMessageService.subscribeToGameChat()
                .doOnNext(message -> {
                    if (message == null) {
                        return;
                    }
                    String sinkKey = message.getKey();
                    Sinks.Many<String> sink = gameChatSinks.get(sinkKey);
                    if (sink != null) {
                        try {
                            String json = objectMapper.writeValueAsString(Map.of(
                                    "type", WebSocketMessageType.CHAT.name(),
                                    "data", message.getData()
                            ));
                            sink.tryEmitNext(json);
                        } catch (Exception e) {
                            log.error("Failed to emit Redis chat message", e);
                        }
                    }
                })
                .onErrorContinue((error, obj) -> log.error("Error in Redis chat subscription, continuing", error))
                .subscribe();

        // 게임 이벤트 구독
        redisMessageService.subscribeToGameEvents()
                .doOnNext(message -> {
                    if (message == null) {
                        return;
                    }
                    String gameId = message.getKey();
                    Sinks.Many<String> sink = gameEventSinks.get(gameId);
                    if (sink != null) {
                        try {
                            String json = objectMapper.writeValueAsString(Map.of(
                                    "type", message.getType().name(),
                                    "data", message.getData()
                            ));
                            sink.tryEmitNext(json);
                        } catch (Exception e) {
                            log.error("Failed to emit Redis event message", e);
                        }
                    }
                })
                .onErrorContinue((error, obj) -> log.error("Error in Redis event subscription, continuing", error))
                .subscribe();
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

        log.info("🔌 WebSocket CONNECTED - GameId: {}, SessionId: {}", gameId, session.getId());

        Sinks.Many<String> sink = getOrCreateEventSink(gameId);
        incrementEventConnectionCount(gameId);

        log.info("📊 Current connections for game {}: {}", gameId, eventConnectionCounts.get(gameId).get());

        Mono<Void> output = createOutputMono(session, sink);
        Mono<Void> input = createInputMono(session);

        return Mono.zip(input, output).then()
                .doFinally(signalType -> {
                    log.info("🔌 WebSocket DISCONNECTED - GameId: {}, SessionId: {}, Signal: {}",
                            gameId, session.getId(), signalType);
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
                k -> {
                    log.info("✨ Creating NEW Sink for gameId: {}", gameId);
                    return Sinks.many().multicast().onBackpressureBuffer();
                });
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
        return session.send(
            sink.asFlux()
                .map(session::textMessage)
                .doOnError(e -> log.error("Error sending WebSocket message", e))
        );
    }

    private Mono<Void> createInputMono(WebSocketSession session) {
        return session.receive()
                .doOnNext(msg -> {
                    try {
                        String text = msg.getPayloadAsText();
                        log.debug("Received message: {}", text);
                    } finally {
                        // DataBuffer 명시적 해제
                        msg.release();
                    }
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

        log.info("GameWebSocketHandler: Received chat event for gameId: {}", gameId);

        // Redis로 발행
        redisMessageService.publishGameChat(gameId, chatType.toString(), event.getChatMessage())
                .doOnSuccess(count -> log.info("Published game chat to Redis"))
                .subscribe();
    }

    @EventListener
    @Async
    public void handlePhaseChangedEvent(PhaseChangedEvent event) {
        String gameId = event.getGameId();
        log.info("GameWebSocketHandler: Received phase changed event for gameId: {}", gameId);

        // 1. 로컬 Sink에 직접 전달 (즉시 처리)
        broadcastToGameEventLocal(gameId, WebSocketMessageType.PHASE_CHANGED, event.getPhaseData());

        // 2. Redis로 발행 (다른 서버로 전파)
        redisMessageService.publishGameEvent(gameId, WebSocketMessageType.PHASE_CHANGED, event.getPhaseData())
                .doOnSuccess(count -> log.info("Published phase changed to Redis"))
                .subscribe();
    }

    @EventListener
    @Async
    public void handlePlayerDiedEvent(PlayerDiedEvent event) {
        String gameId = event.getGameId();
        log.info("GameWebSocketHandler: Received player died event for gameId: {}", gameId);

        Map<String, Object> data = Map.of(
                "gameId", event.getGameId(),
                "deadPlayerIds", event.getDeadPlayerIds(),
                "reason", event.getReason()
        );

        // 1. 로컬 Sink에 직접 전달
        broadcastToGameEventLocal(gameId, WebSocketMessageType.PLAYER_DIED, data);

        // 2. Redis로 발행
        redisMessageService.publishGameEvent(gameId, WebSocketMessageType.PLAYER_DIED, data)
                .doOnSuccess(count -> log.info("Published player died to Redis"))
                .subscribe();
    }

    @EventListener
    @Async
    public void handleGameEndedEvent(GameEndedEvent event) {
        String gameId = event.getGameId();
        log.info("GameWebSocketHandler: Received game ended event for gameId: {}", gameId);

        Map<String, Object> data = Map.of(
                "gameId", event.getGameId(),
                "winnerTeam", event.getWinnerTeam()
        );

        // 1. 로컬 Sink에 직접 전달
        broadcastToGameEventLocal(gameId, WebSocketMessageType.GAME_ENDED, data);

        // 2. Redis로 발행
        redisMessageService.publishGameEvent(gameId, WebSocketMessageType.GAME_ENDED, data)
                .doOnSuccess(count -> log.info("Published game ended to Redis"))
                .subscribe();
    }

    private boolean isGameChat(ChatType chatType) {
        return chatType != ChatType.WAITING_ROOM;
    }

    private String buildSinkKey(String gameId, ChatType chatType) {
        return gameId + "-" + chatType;
    }

    /**
     * 로컬 Sink로 직접 게임 이벤트 브로드캐스트
     * Redis 구독 실패 시에도 현재 서버의 클라이언트에게 메시지 전달
     */
    private void broadcastToGameEventLocal(String gameId, WebSocketMessageType type, Object data) {
        Sinks.Many<String> sink = gameEventSinks.get(gameId);

        log.info("📤 Broadcasting {} to gameId: {}, Sink exists: {}", type, gameId, (sink != null));

        if (sink == null) {
            log.warn("⚠️ No local sink found for gameId: {} (no clients connected to this server)", gameId);
            log.info("Available sinks: {}", gameEventSinks.keySet());
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", type.name(),
                    "data", data
            ));

            log.info("📨 Sending message: {}", json.substring(0, Math.min(100, json.length())) + "...");

            Sinks.EmitResult result = sink.tryEmitNext(json);

            if (result.isFailure()) {
                log.error("❌ Failed to emit {} to local sink for game {}: {}", type, gameId, result);
            } else {
                log.info("✅ Successfully emitted {} to local sink for gameId: {}", type, gameId);
            }
        } catch (Exception e) {
            log.error("💥 Exception while broadcasting {} to local sink", type, e);
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
