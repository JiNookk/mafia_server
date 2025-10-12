package com.jingwook.mafia_server.repositories;

import com.jingwook.mafia_server.entities.RoomMemberEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface RoomMemberR2dbcRepository extends R2dbcRepository<RoomMemberEntity, Long> {

    Flux<RoomMemberEntity> findByRoomId(String roomId);

    Mono<RoomMemberEntity> findByUserId(String userId);

    Mono<RoomMemberEntity> findByRoomIdAndUserId(String roomId, String userId);

    @Query("SELECT room_id FROM room_members WHERE user_id = :userId LIMIT 1")
    Mono<String> findRoomIdByUserId(String userId);

    Mono<Boolean> existsByUserId(String userId);

    Mono<Boolean> existsByRoomIdAndUserId(String roomId, String userId);

    Mono<Void> deleteByRoomIdAndUserId(String roomId, String userId);

    Mono<Void> deleteByRoomId(String roomId);

    @Query("SELECT COUNT(*) FROM room_members WHERE room_id = :roomId")
    Mono<Long> countByRoomId(String roomId);
}