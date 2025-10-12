package com.jingwook.mafia_server.repositories;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import com.jingwook.mafia_server.domains.User;
import com.jingwook.mafia_server.entities.UserEntity;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public class UserRepository {
    private final UserR2dbcRepository userR2dbcRepository;

    public UserRepository(UserR2dbcRepository userR2dbcRepository) {
        this.userR2dbcRepository = userR2dbcRepository;
    }

    public Mono<User> findByUsername(String userName) {
        return userR2dbcRepository.findByNickname(userName)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")))
                .map(this::entityToDomain);
    }

    public Mono<User> findById(String id) {
        return userR2dbcRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")))
                .map(this::entityToDomain);
    }

    public Mono<User> insert(User user) {
        LocalDateTime now = LocalDateTime.now();
        return userR2dbcRepository.insert(
                user.getId(),
                user.getNickname(),
                user.getJoinedAt(),
                now,
                now
        ).thenReturn(user);
    }

    public Mono<User> save(User user) {
        UserEntity entity = domainToEntity(user);
        return userR2dbcRepository.save(entity)
                .map(this::entityToDomain);
    }

    public Mono<Boolean> existsByNickname(String nickname) {
        return userR2dbcRepository.existsByNickname(nickname);
    }

    private User entityToDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getNickname(),
                entity.getJoinedAt()
        );
    }

    private UserEntity domainToEntity(User user) {
        return UserEntity.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .joinedAt(user.getJoinedAt())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
