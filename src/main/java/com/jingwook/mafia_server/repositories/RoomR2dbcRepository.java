package com.jingwook.mafia_server.repositories;

import com.jingwook.mafia_server.entities.RoomEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface RoomR2dbcRepository extends R2dbcRepository<RoomEntity, Long> {

    Mono<RoomEntity> findByRoomId(String roomId);

    @Query("SELECT * FROM rooms ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<RoomEntity> findAllWithPagination(long offset, long limit);

    @Query("SELECT COUNT(*) FROM rooms")
    Mono<Long> countAll();

    Mono<Boolean> existsByRoomId(String roomId);

    @Modifying
    @Query("UPDATE rooms SET status = :status WHERE room_id = :roomId")
    Mono<Integer> updateStatus(String roomId, String status);
}