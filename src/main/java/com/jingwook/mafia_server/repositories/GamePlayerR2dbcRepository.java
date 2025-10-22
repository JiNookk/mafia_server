package com.jingwook.mafia_server.repositories;

import com.jingwook.mafia_server.entities.GamePlayerEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface GamePlayerR2dbcRepository extends R2dbcRepository<GamePlayerEntity, String> {

    Flux<GamePlayerEntity> findByGameId(String gameId);

    Mono<GamePlayerEntity> findByGameIdAndUserId(String gameId, String userId);

    @Query("SELECT * FROM game_players WHERE game_id = :gameId AND is_alive = :isAlive")
    Flux<GamePlayerEntity> findByGameIdAndIsAlive(String gameId, Boolean isAlive);

    @Query("SELECT * FROM game_players WHERE game_id = :gameId AND is_alive = :isAlive AND role = :role")
    Flux<GamePlayerEntity> findByGameIdAndIsAliveAndRole(String gameId, Boolean isAlive, String role);

    @Query("SELECT * FROM game_players WHERE game_id = :gameId AND role = :role")
    Flux<GamePlayerEntity> findByGameIdAndRole(String gameId, String role);

    @Query("SELECT COUNT(*) FROM game_players WHERE game_id = :gameId AND is_alive = :isAlive")
    Mono<Long> countByGameIdAndIsAlive(String gameId, Boolean isAlive);

    @Query("SELECT COUNT(*) FROM game_players WHERE game_id = :gameId AND is_alive = :isAlive AND role = :role")
    Mono<Long> countByGameIdAndIsAliveAndRole(String gameId, Boolean isAlive, String role);
}
