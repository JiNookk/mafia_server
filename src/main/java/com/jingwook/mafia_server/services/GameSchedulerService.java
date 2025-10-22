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

    // 현재 처리 중인 게임 ID를 추적하여 중복 처리 방지
    private final Set<String> processingGames = ConcurrentHashMap.newKeySet();

    public GameSchedulerService(GameR2dbcRepository gameRepository, GameService gameService) {
        this.gameRepository = gameRepository;
        this.gameService = gameService;
    }

    /**
     * 1초마다 진행 중인 게임들의 페이즈 시간을 체크하여 자동으로 다음 페이즈로 전환
     */
    @Scheduled(fixedRate = 1000)
    public void checkPhaseTimeouts() {
        gameRepository.findAllActiveGames()
                .filter(this::isPhaseExpired)
                .filter(game -> processingGames.add(game.getId())) // 중복 처리 방지
                .flatMap(this::processExpiredGame)
                .subscribe();
    }

    /**
     * 도메인 로직을 사용하여 페이즈 만료 확인
     */
    private boolean isPhaseExpired(GameEntity entity) {
        return entity.toDomain().isPhaseExpired();
    }

    private Mono<Void> processExpiredGame(GameEntity game) {
        log.info("Auto phase transition triggered for game: {}", game.getId());
        return gameService.nextPhase(game.getId())
                .doFinally(signal -> processingGames.remove(game.getId())) // 처리 완료 후 제거
                .then()
                .onErrorResume(error -> handlePhaseTransitionError(game.getId(), error));
    }

    private Mono<Void> handlePhaseTransitionError(String gameId, Throwable error) {
        log.error("Failed to auto-transition phase for game: {}", gameId, error);
        return Mono.empty();
    }
}
