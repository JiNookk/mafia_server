package com.jingwook.mafia_server.services;

import static com.jingwook.mafia_server.utils.Constants.NICKNAME_PREFIX;
import static com.jingwook.mafia_server.utils.Constants.SESSION_PREFIX;
import static com.jingwook.mafia_server.utils.Constants.SESSION_TTL;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jingwook.mafia_server.domains.User;
import com.jingwook.mafia_server.dtos.SessionResponseDto;
import com.jingwook.mafia_server.repositories.UserRepository;

import reactor.core.publisher.Mono;

@Service
public class AuthService {
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public AuthService(ReactiveRedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper,
            UserRepository userRepository) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    private Mono<Boolean> checkNicknameExists(String nickname) {
        return redisTemplate.hasKey(NICKNAME_PREFIX + nickname);
    }

    public Mono<SessionResponseDto> signup(String nickname) {
        return checkNicknameExists(nickname)
                .flatMap(exists -> exists
                        ? renewUserSession(nickname)
                        : createUserSession(nickname));
    }

    private Mono<SessionResponseDto> renewUserSession(String nickname) {
        return redisTemplate.opsForValue()
                .get(NICKNAME_PREFIX + nickname)
                .flatMap(sessionId -> refreshNickNameTTL(nickname)
                        .thenReturn(
                                new SessionResponseDto(sessionId, nickname)));
    }

    private Mono<SessionResponseDto> createUserSession(String nickname) {
        String sessionId = UUID.randomUUID().toString();
        User user = new User(sessionId, nickname, LocalDateTime.now());

        try {
            String userJson = objectMapper.writeValueAsString(user);

            return redisTemplate.opsForValue()
                    .set(SESSION_PREFIX + sessionId, userJson)
                    .then(refreshSessionTTL(sessionId))
                    .then(redisTemplate.opsForValue()
                            .set(NICKNAME_PREFIX + nickname, sessionId))
                    .then(refreshNickNameTTL(nickname))
                    .thenReturn(new SessionResponseDto(sessionId, nickname));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private Mono<Boolean> refreshNickNameTTL(String nickname) {
        return redisTemplate.expire(NICKNAME_PREFIX + nickname, SESSION_TTL);
    }

    private Mono<Boolean> refreshSessionTTL(String sessionId) {
        return redisTemplate.expire(SESSION_PREFIX + sessionId, SESSION_TTL);
    }

    public Mono<Boolean> checkSession(String sessionId) {
        if (sessionId == null) {
            return Mono.error(new RuntimeException("SessionId is required"));
        }

        return redisTemplate.opsForValue()
                .get(SESSION_PREFIX + sessionId)
                .flatMap(exist -> {
                    return userRepository.findById(sessionId)
                            .flatMap(user -> Mono.when(
                                    refreshSessionTTL(sessionId),
                                    refreshNickNameTTL(user.getNickname())).thenReturn(exist != null));
                });
    };

}
