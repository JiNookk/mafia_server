package com.jingwook.mafia_server.repositories;

import com.jingwook.mafia_server.entities.UserEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface UserR2dbcRepository extends R2dbcRepository<UserEntity, String> {

    Mono<UserEntity> findByNickname(String nickname);

    Mono<Boolean> existsByNickname(String nickname);

    @Modifying
    @Query("INSERT INTO users (id, nickname, joined_at, created_at, updated_at) " +
           "VALUES (:id, :nickname, :joinedAt, :createdAt, :updatedAt)")
    Mono<Integer> insert(String id, String nickname, LocalDateTime joinedAt,
                         LocalDateTime createdAt, LocalDateTime updatedAt);
}