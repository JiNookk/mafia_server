package com.jingwook.mafia_server.repositories;

import com.jingwook.mafia_server.entities.GameEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface GameR2dbcRepository extends R2dbcRepository<GameEntity, String> {

    Mono<GameEntity> findByRoomId(String roomId);

    @Query("SELECT * FROM games WHERE room_id = :roomId AND finished_at IS NULL")
    Mono<GameEntity> findActiveGameByRoomId(String roomId);

    @Query("SELECT * FROM games WHERE finished_at IS NULL")
    Flux<GameEntity> findAllActiveGames();
}
