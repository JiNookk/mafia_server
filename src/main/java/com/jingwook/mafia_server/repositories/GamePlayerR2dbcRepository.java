package com.jingwook.mafia_server.repositories;

import com.jingwook.mafia_server.entities.GamePlayerEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface GamePlayerR2dbcRepository extends R2dbcRepository<GamePlayerEntity, Long> {

    Flux<GamePlayerEntity> findByGameId(Long gameId);

    Mono<GamePlayerEntity> findByGameIdAndUserId(Long gameId, Long userId);

    @Query("SELECT * FROM game_players WHERE game_id = :gameId AND is_alive = :isAlive")
    Flux<GamePlayerEntity> findByGameIdAndIsAlive(Long gameId, Boolean isAlive);

    @Query("SELECT * FROM game_players WHERE game_id = :gameId AND is_alive = :isAlive AND role = :role")
    Flux<GamePlayerEntity> findByGameIdAndIsAliveAndRole(Long gameId, Boolean isAlive, String role);

    @Query("SELECT * FROM game_players WHERE game_id = :gameId AND role = :role")
    Flux<GamePlayerEntity> findByGameIdAndRole(Long gameId, String role);

    @Query("SELECT COUNT(*) FROM game_players WHERE game_id = :gameId AND is_alive = :isAlive")
    Mono<Long> countByGameIdAndIsAlive(Long gameId, Boolean isAlive);

    @Query("SELECT COUNT(*) FROM game_players WHERE game_id = :gameId AND is_alive = :isAlive AND role = :role")
    Mono<Long> countByGameIdAndIsAliveAndRole(Long gameId, Boolean isAlive, String role);
}
