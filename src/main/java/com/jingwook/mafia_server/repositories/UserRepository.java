package com.jingwook.mafia_server.repositories;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;

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
        return redisTemplate.opsForValue().get(userName).flatMap(userJsonString -> {

            if (userJsonString == null) {
                return Mono.error(new RuntimeException("User not found"));
            }

            try {
                User user = objectMapper.readValue(userJsonString, User.class);

                return Mono.just(user);

            } catch (JsonProcessingException e) {
                return Mono.error(new RuntimeException(e));
            }

        });
    }

}
