package com.jingwook.mafia_server.repositories;

import static com.jingwook.mafia_server.utils.Constants.NICKNAME_PREFIX;
import static com.jingwook.mafia_server.utils.Constants.SESSION_PREFIX;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jingwook.mafia_server.domains.User;

import reactor.core.publisher.Mono;

@Repository
public class UserRepository {
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public UserRepository(ReactiveRedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Mono<User> findByUsername(String userName) {
        return redisTemplate.opsForValue().get(NICKNAME_PREFIX + userName)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")))
                .flatMap(sessionId -> findById(sessionId));
    }

    public Mono<User> findById(String sessionId) {
        return redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")))
                .flatMap(userJsonString -> {

                    try {
                        User user = objectMapper.readValue(userJsonString, User.class);

                        return Mono.just(user);

                    } catch (JsonProcessingException e) {
                        return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                "Failed to parse user data", e));
                    }

                });
    }

}
