package com.jingwook.mafia_server.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis 기반 분산 락 서비스
 * 여러 서버 인스턴스에서 동시에 같은 작업을 하지 않도록 보장
 */
@Service
public class RedisLockService {
    private static final Logger log = LoggerFactory.getLogger(RedisLockService.class);
    private static final String LOCK_PREFIX = "lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration RETRY_DELAY = Duration.ofMillis(100);
    private static final int MAX_RETRY_ATTEMPTS = 50;

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public RedisLockService(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 락 획득 시도 (재시도 포함)
     * @param lockKey 락 키
     * @return 락 토큰 (해제 시 필요)
     */
    public Mono<String> acquireLock(String lockKey) {
        String fullKey = LOCK_PREFIX + lockKey;
        String lockToken = UUID.randomUUID().toString();

        return redisTemplate.opsForValue()
                .setIfAbsent(fullKey, lockToken, LOCK_TTL)
                .flatMap(acquired -> {
                    if (acquired) {
                        log.debug("Lock acquired: {} with token: {}", fullKey, lockToken);
                        return Mono.just(lockToken);
                    } else {
                        return Mono.error(new LockNotAcquiredException("Failed to acquire lock: " + fullKey));
                    }
                })
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_DELAY)
                        .filter(throwable -> throwable instanceof LockNotAcquiredException)
                        .doBeforeRetry(signal -> log.debug("Retrying to acquire lock: {}", fullKey)))
                .doOnError(error -> log.warn("Failed to acquire lock after retries: {}", fullKey));
    }

    /**
     * 락 해제
     * @param lockKey 락 키
     * @param lockToken 락 획득 시 받은 토큰
     */
    public Mono<Void> releaseLock(String lockKey, String lockToken) {
        String fullKey = LOCK_PREFIX + lockKey;

        return redisTemplate.opsForValue()
                .get(fullKey)
                .flatMap(currentToken -> {
                    if (lockToken.equals(currentToken.toString())) {
                        return redisTemplate.delete(fullKey)
                                .doOnSuccess(count -> log.debug("Lock released: {}", fullKey))
                                .then();
                    } else {
                        log.warn("Lock token mismatch for key: {}. Lock not released.", fullKey);
                        return Mono.empty();
                    }
                })
                .doOnError(error -> log.error("Failed to release lock: {}", fullKey, error))
                .then();
    }

    /**
     * 락을 획득하고 작업을 수행한 후 자동으로 락 해제
     * @param lockKey 락 키
     * @param task 수행할 작업
     */
    public <T> Mono<T> executeWithLock(String lockKey, Mono<T> task) {
        return acquireLock(lockKey)
                .flatMap(lockToken -> task
                        .doFinally(signalType -> releaseLock(lockKey, lockToken).subscribe())
                );
    }

    /**
     * 락 획득 실패 예외
     */
    public static class LockNotAcquiredException extends RuntimeException {
        public LockNotAcquiredException(String message) {
            super(message);
        }
    }
}
