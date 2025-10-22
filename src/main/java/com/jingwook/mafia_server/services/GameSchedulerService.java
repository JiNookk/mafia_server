package com.jingwook.mafia_server.services;

import com.jingwook.mafia_server.entities.GameEntity;
import com.jingwook.mafia_server.repositories.GameR2dbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameSchedulerService {
    private static final Logger log = LoggerFactory.getLogger(GameSchedulerService.class);

    private final GameR2dbcRepository gameRepository;
    private final GameService gameService;
    private final RedisLockService redisLockService;

    public GameSchedulerService(
            GameR2dbcRepository gameRepository,
            GameService gameService,
            RedisLockService redisLockService) {
        this.gameRepository = gameRepository;
        this.gameService = gameService;
        this.redisLockService = redisLockService;
    }

    /**
     * 1초마다 진행 중인 게임들의 페이즈 시간을 체크하여 자동으로 다음 페이즈로 전환
     * Redis 분산 락을 사용하여 여러 서버에서 중복 처리 방지
     */
    @Scheduled(fixedRate = 1000)
    public void checkPhaseTimeouts() {
        gameRepository.findAllActiveGames()
                .doOnNext(game -> log.debug("🔍 Checking game: {}, phase: {}, started: {}",
                    game.getId(), game.getCurrentPhase(), game.getPhaseStartTime()))
                .filter(this::isPhaseExpired)
                .doOnNext(game -> log.info("⏰ Phase expired for game: {}", game.getId()))
                .flatMap(this::processExpiredGameWithLock)
                .doOnError(error -> log.error("❌ Error in scheduler", error))
                .subscribe();
    }

    /**
     * 도메인 로직을 사용하여 페이즈 만료 확인
     */
    private boolean isPhaseExpired(GameEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime phaseStart = entity.getPhaseStartTime();
        Integer duration = entity.getPhaseDurationSeconds();

        if (phaseStart == null || duration == null) {
            log.debug("⚠️ Phase check skipped - null values: start={}, duration={}", phaseStart, duration);
            return false;
        }

        LocalDateTime expireTime = phaseStart.plusSeconds(duration);
        boolean expired = now.isAfter(expireTime);

        log.debug("⏱️ Phase check - game: {}, now: {}, expire: {}, expired: {}",
            entity.getId(), now, expireTime, expired);

        if (expired) {
            log.info("⏰ Phase expired for game: {}, phase: {}, started: {}, duration: {}s",
                entity.getId(), entity.getCurrentPhase(), phaseStart, duration);
        }
        return expired;
    }

    /**
     * 분산 락을 사용하여 게임 처리
     * 락 키: phase:transition:{gameId}
     */
    private Mono<Void> processExpiredGameWithLock(GameEntity game) {
        String lockKey = "phase:transition:" + game.getId();

        return redisLockService.executeWithLock(lockKey, processExpiredGame(game))
                .onErrorResume(RedisLockService.LockNotAcquiredException.class, error -> {
                    log.debug("Another server is processing game: {}", game.getId());
                    return Mono.empty();
                })
                .onErrorResume(error -> handlePhaseTransitionError(game.getId(), error));
    }

    private Mono<Void> processExpiredGame(GameEntity game) {
        log.info("Auto phase transition triggered for game: {}", game.getId());
        return gameService.nextPhase(game.getId())
                .then()
                .onErrorResume(error -> handlePhaseTransitionError(game.getId(), error));
    }

    private Mono<Void> handlePhaseTransitionError(String gameId, Throwable error) {
        log.error("Failed to auto-transition phase for game: {}", gameId, error);
        return Mono.empty();
    }
}
