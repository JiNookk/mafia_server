package com.jingwook.mafia_server.repositories;

import com.jingwook.mafia_server.entities.GameActionEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface GameActionR2dbcRepository extends R2dbcRepository<GameActionEntity, String> {

    @Query("SELECT * FROM game_actions WHERE game_id = :gameId AND day_count = :dayCount AND phase = :phase AND type = :type")
    Flux<GameActionEntity> findByGameIdAndDayCountAndPhaseAndType(String gameId, Integer dayCount, String phase, String type);

    @Query("SELECT * FROM game_actions WHERE game_id = :gameId AND day_count = :dayCount AND type = :type")
    Flux<GameActionEntity> findByGameIdAndDayCountAndType(String gameId, Integer dayCount, String type);

    @Query("SELECT * FROM game_actions WHERE game_id = :gameId AND day_count = :dayCount AND phase = :phase")
    Flux<GameActionEntity> findByGameIdAndDayCountAndPhase(String gameId, Integer dayCount, String phase);

    @Query("SELECT * FROM game_actions WHERE game_id = :gameId AND actor_user_id = :actorUserId AND day_count = :dayCount AND type = :type")
    Mono<GameActionEntity> findByGameIdAndActorUserIdAndDayCountAndType(String gameId, String actorUserId, Integer dayCount, String type);

    @Modifying
    @Query("DELETE FROM game_actions WHERE game_id = :gameId AND actor_user_id = :actorUserId AND day_count = :dayCount AND type = :type")
    Mono<Integer> deleteByGameIdAndActorUserIdAndDayCountAndType(String gameId, String actorUserId, Integer dayCount, String type);

    @Query("SELECT COUNT(*) FROM game_actions WHERE game_id = :gameId AND day_count = :dayCount AND type = :type AND target_user_id = :targetUserId")
    Mono<Long> countByGameIdAndDayCountAndTypeAndTargetUserId(String gameId, Integer dayCount, String type, String targetUserId);

    @Query("SELECT * FROM game_actions WHERE game_id = :gameId AND actor_user_id = :actorUserId AND type = :type ORDER BY day_count DESC")
    Flux<GameActionEntity> findByGameIdAndActorUserIdAndType(String gameId, String actorUserId, String type);
}
