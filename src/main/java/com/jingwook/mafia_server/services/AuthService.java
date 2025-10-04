package com.jingwook.mafia_server.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jingwook.mafia_server.domains.User;
import com.jingwook.mafia_server.dtos.SessionResponseDto;
import com.jingwook.mafia_server.exceptions.UserAlreadyExistException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

public class AuthService {
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;


    private static final String SESSION_PREFIX = "session:";
    private static final String NICKNAME_PREFIX = "nickname:";
    private static final Duration SESSION_TTL    = Duration.ofHours(1);

    public AuthService(ReactiveRedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper){
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }


    private Mono<Boolean> checkNicknameExists(String nickname){
        return redisTemplate.hasKey(NICKNAME_PREFIX + nickname);
    }

    public Mono<SessionResponseDto> signup(String nickname) {
        return checkNicknameExists(nickname)
                .flatMap(exists -> exists ?
                        Mono.error(new UserAlreadyExistException("User already exists!"))
                        : createUserSession(nickname)
                );
    }

    private Mono<SessionResponseDto> createUserSession(String nickname) {
        String sessionId = UUID.randomUUID().toString();
        User user = new User(sessionId, nickname, LocalDateTime.now());


        try{
            String userJson = objectMapper.writeValueAsString(user);

            return redisTemplate.opsForValue()
                    .set(SESSION_PREFIX + sessionId, userJson)
                    .then(
                            redisTemplate.expire(SESSION_PREFIX + sessionId, SESSION_TTL)
                    )
                    .then(
                            redisTemplate.opsForValue()
                                    .set(NICKNAME_PREFIX + nickname, sessionId)
                    ).then(redisTemplate.expire(NICKNAME_PREFIX + nickname, SESSION_TTL))
                    .thenReturn(new SessionResponseDto(sessionId, nickname));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    };

}
