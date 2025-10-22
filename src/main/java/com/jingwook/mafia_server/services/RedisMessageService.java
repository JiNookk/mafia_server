package com.jingwook.mafia_server.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jingwook.mafia_server.enums.WebSocketMessageType;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RedisMessageService {
    private static final Logger log = LoggerFactory.getLogger(RedisMessageService.class);

    private final ReactiveRedisTemplate<String, String> stringRedisTemplate;
    private final ReactiveRedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;
    private final ChannelTopic roomUpdateTopic;
    private final ChannelTopic gameChatTopic;
    private final ChannelTopic gameEventTopic;

    public RedisMessageService(
            ReactiveRedisConnectionFactory connectionFactory,
            ReactiveRedisMessageListenerContainer listenerContainer,
            ObjectMapper objectMapper,
            ChannelTopic roomUpdateTopic,
            ChannelTopic gameChatTopic,
            ChannelTopic gameEventTopic) {
        // Pub/Sub용 String 전용 RedisTemplate 생성
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        RedisSerializationContext<String, String> serializationContext = RedisSerializationContext
                .<String, String>newSerializationContext(stringSerializer)
                .value(stringSerializer)
                .build();
        this.stringRedisTemplate = new ReactiveRedisTemplate<>(connectionFactory, serializationContext);

        this.listenerContainer = listenerContainer;
        this.objectMapper = objectMapper;
        this.roomUpdateTopic = roomUpdateTopic;
        this.gameChatTopic = gameChatTopic;
        this.gameEventTopic = gameEventTopic;
    }

    /**
     * 방 업데이트 메시지 발행
     */
    public Mono<Long> publishRoomUpdate(String roomId, Object data) {
        return publishMessage(roomUpdateTopic, createMessage(roomId, WebSocketMessageType.ROOM_UPDATE, data));
    }

    /**
     * 게임 채팅 메시지 발행
     */
    public Mono<Long> publishGameChat(String gameId, String chatType, Object data) {
        String key = gameId + ":" + chatType;
        return publishMessage(gameChatTopic, createMessage(key, WebSocketMessageType.CHAT, data));
    }

    /**
     * 게임 이벤트 메시지 발행
     */
    public Mono<Long> publishGameEvent(String gameId, WebSocketMessageType eventType, Object data) {
        return publishMessage(gameEventTopic, createMessage(gameId, eventType, data));
    }

    /**
     * 방 업데이트 메시지 구독
     */
    public Flux<RedisMessage> subscribeToRoomUpdates() {
        return subscribeToTopic(roomUpdateTopic);
    }

    /**
     * 게임 채팅 메시지 구독
     */
    public Flux<RedisMessage> subscribeToGameChat() {
        return subscribeToTopic(gameChatTopic);
    }

    /**
     * 게임 이벤트 메시지 구독
     */
    public Flux<RedisMessage> subscribeToGameEvents() {
        return subscribeToTopic(gameEventTopic);
    }

    private Mono<Long> publishMessage(ChannelTopic topic, RedisMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            return stringRedisTemplate.convertAndSend(topic.getTopic(), json)
                    .doOnSuccess(count -> log.debug("Published message to {}: {}", topic.getTopic(), json))
                    .doOnError(error -> log.error("Failed to publish message to {}", topic.getTopic(), error));
        } catch (Exception e) {
            log.error("Failed to serialize message for topic {}", topic.getTopic(), e);
            return Mono.just(0L);
        }
    }

    private Flux<RedisMessage> subscribeToTopic(ChannelTopic topic) {
        return listenerContainer.receive(topic)
                .mapNotNull(message -> {
                    try {
                        // ReactiveRedisMessageListenerContainer는 ByteBuffer를 반환
                        Object messageObj = message.getMessage();

                        String json;
                        if (messageObj instanceof byte[]) {
                            json = new String((byte[]) messageObj, java.nio.charset.StandardCharsets.UTF_8);
                        } else if (messageObj instanceof java.nio.ByteBuffer) {
                            java.nio.ByteBuffer buffer = (java.nio.ByteBuffer) messageObj;
                            byte[] bytes = new byte[buffer.remaining()];
                            buffer.get(bytes);
                            json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                        } else if (messageObj instanceof String) {
                            json = (String) messageObj;
                        } else {
                            log.warn("Unexpected message type: {}", messageObj.getClass().getName());
                            json = messageObj.toString();
                        }

                        log.debug("Received from {}: {}", topic.getTopic(), json);

                        // 이중 직렬화 확인 및 언래핑
                        // Redis MONITOR에서 "\"{...}\"" 형태로 보이면 이중 직렬화됨
                        if (json.startsWith("\"") && json.endsWith("\"") && json.length() > 2) {
                            // 이스케이프된 JSON 문자열을 언래핑
                            json = objectMapper.readValue(json, String.class);
                            log.debug("Unwrapped: {}", json);
                        }

                        return objectMapper.readValue(json, RedisMessage.class);
                    } catch (Exception e) {
                        log.error("Failed to deserialize message from {}", topic.getTopic(), e);
                        return null;
                    }
                });
    }

    private RedisMessage createMessage(String key, WebSocketMessageType type, Object data) {
        return new RedisMessage(key, type, data);
    }

    /**
     * Redis로 전송되는 메시지 포맷
     */
    public static class RedisMessage {
        private String key; // roomId, gameId, or gameId:chatType
        private String type; // WebSocketMessageType을 String으로 저장
        private Object data;

        public RedisMessage() {
        }

        public RedisMessage(String key, WebSocketMessageType type, Object data) {
            this.key = key;
            this.type = type.name();
            this.data = data;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public WebSocketMessageType getType() {
            return type != null ? WebSocketMessageType.valueOf(type) : null;
        }

        public String getTypeString() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }
}
