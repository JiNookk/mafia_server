package com.jingwook.mafia_server.services;

import com.jingwook.mafia_server.enums.ActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 게임 액션을 Redis에 임시 저장하는 서비스
 * 페이즈 진행 중에는 Redis에만 저장하고, 페이즈 종료 시 DB에 저장
 */
@Service
public class GameActionCacheService {
    private static final Logger log = LoggerFactory.getLogger(GameActionCacheService.class);
    private static final String ACTION_PREFIX = "game:action:";
    private static final Duration ACTION_TTL = Duration.ofHours(1);

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public GameActionCacheService(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 액션 저장 (같은 플레이어의 같은 타입 액션은 덮어쓰기)
     * key: game:action:{gameId}:{dayCount}:{actionType}:{actorUserId}
     */
    public Mono<Void> saveAction(String gameId, int dayCount, ActionType actionType,
                                   String actorUserId, String targetUserId) {
        String key = buildActionKey(gameId, dayCount, actionType, actorUserId);

        Map<String, Object> actionData = new HashMap<>();
        actionData.put("gameId", gameId);
        actionData.put("dayCount", dayCount);
        actionData.put("actionType", actionType.toString());
        actionData.put("actorUserId", actorUserId);
        actionData.put("targetUserId", targetUserId);

        return redisTemplate.opsForValue()
                .set(key, actionData, ACTION_TTL)
                .doOnSuccess(success -> log.debug("Saved action to cache: {}", key))
                .doOnError(error -> log.error("Failed to save action to cache: {}", key, error))
                .then();
    }

    /**
     * 특정 타입의 액션 조회
     * pattern: game:action:{gameId}:{dayCount}:{actionType}:*
     */
    @SuppressWarnings("unchecked")
    public Flux<Map<String, Object>> getActionsByType(String gameId, int dayCount, ActionType actionType) {
        String pattern = buildActionPattern(gameId, dayCount, actionType);

        return redisTemplate.keys(pattern)
                .flatMap(key -> redisTemplate.opsForValue().get(key))
                .map(obj -> (Map<String, Object>) obj)
                .doOnComplete(() -> log.debug("Retrieved actions from cache for pattern: {}", pattern))
                .doOnError(error -> log.error("Failed to get actions from cache for pattern: {}", pattern, error));
    }

    /**
     * 특정 플레이어의 액션 삭제
     */
    public Mono<Void> deleteAction(String gameId, int dayCount, ActionType actionType, String actorUserId) {
        String key = buildActionKey(gameId, dayCount, actionType, actorUserId);

        return redisTemplate.delete(key)
                .doOnSuccess(count -> log.debug("Deleted action from cache: {}", key))
                .doOnError(error -> log.error("Failed to delete action from cache: {}", key, error))
                .then();
    }

    /**
     * 특정 게임의 특정 일차 모든 액션 삭제
     */
    public Mono<Void> clearDayActions(String gameId, int dayCount) {
        String pattern = ACTION_PREFIX + gameId + ":" + dayCount + ":*";

        return redisTemplate.keys(pattern)
                .flatMap(redisTemplate::delete)
                .doOnComplete(() -> log.debug("Cleared all actions for game {} day {}", gameId, dayCount))
                .doOnError(error -> log.error("Failed to clear actions for game {} day {}", gameId, dayCount, error))
                .then();
    }

    private String buildActionKey(String gameId, int dayCount, ActionType actionType, String actorUserId) {
        return ACTION_PREFIX + gameId + ":" + dayCount + ":" + actionType + ":" + actorUserId;
    }

    private String buildActionPattern(String gameId, int dayCount, ActionType actionType) {
        return ACTION_PREFIX + gameId + ":" + dayCount + ":" + actionType + ":*";
    }
}
