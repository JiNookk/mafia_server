package com.jingwook.mafia_server.services;

import com.jingwook.mafia_server.dtos.GameStateResponse;
import com.jingwook.mafia_server.entities.GameEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 게임 상태를 Redis에 캐싱하는 서비스
 */
@Service
public class GameCacheService {
    private static final Logger log = LoggerFactory.getLogger(GameCacheService.class);
    private static final String GAME_STATE_PREFIX = "game:state:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public GameCacheService(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 게임 상태 캐시에 저장
     */
    public Mono<Void> cacheGameState(String gameId, GameEntity gameEntity) {
        String key = GAME_STATE_PREFIX + gameId;
        return redisTemplate.opsForValue()
                .set(key, gameEntity, CACHE_TTL)
                .doOnSuccess(success -> log.debug("Cached game state for gameId: {}", gameId))
                .doOnError(error -> log.error("Failed to cache game state for gameId: {}", gameId, error))
                .then();
    }

    /**
     * 게임 상태 캐시에서 조회
     */
    public Mono<GameEntity> getGameStateFromCache(String gameId) {
        String key = GAME_STATE_PREFIX + gameId;
        return redisTemplate.opsForValue()
                .get(key)
                .cast(GameEntity.class)
                .doOnSuccess(entity -> {
                    if (entity != null) {
                        log.debug("Cache hit for gameId: {}", gameId);
                    } else {
                        log.debug("Cache miss for gameId: {}", gameId);
                    }
                })
                .doOnError(error -> log.error("Failed to get game state from cache for gameId: {}", gameId, error));
    }

    /**
     * 게임 상태 캐시 무효화
     */
    public Mono<Void> invalidateGameState(String gameId) {
        String key = GAME_STATE_PREFIX + gameId;
        return redisTemplate.delete(key)
                .doOnSuccess(count -> log.debug("Invalidated cache for gameId: {}", gameId))
                .doOnError(error -> log.error("Failed to invalidate cache for gameId: {}", gameId, error))
                .then();
    }

    /**
     * 게임 종료 시 캐시 삭제
     */
    public Mono<Void> removeGameState(String gameId) {
        return invalidateGameState(gameId);
    }
}
