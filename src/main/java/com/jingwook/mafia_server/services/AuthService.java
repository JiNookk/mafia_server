package com.jingwook.mafia_server.services;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;
import com.jingwook.mafia_server.domains.User;
import com.jingwook.mafia_server.dtos.SessionResponseDto;
import com.jingwook.mafia_server.repositories.UserRepository;

import reactor.core.publisher.Mono;

@Service
public class AuthService {
    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private Mono<Boolean> checkNicknameExists(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    @Transactional
    public Mono<SessionResponseDto> signup(String nickname) {
        return checkNicknameExists(nickname)
                .flatMap(exists -> exists
                        ? renewUserSession(nickname)
                        : createUserSession(nickname));
    }

    private Mono<SessionResponseDto> renewUserSession(String nickname) {
        return userRepository.findByUsername(nickname)
                .map(user -> new SessionResponseDto(user.getId(), nickname));
    }

    private Mono<SessionResponseDto> createUserSession(String nickname) {
        String userId = UuidCreator.getTimeOrderedEpoch().toString();
        User user = new User(userId, nickname, LocalDateTime.now());

        return userRepository.insert(user)
                .map(savedUser -> new SessionResponseDto(savedUser.getId(), nickname));
    }

    public Mono<Boolean> checkSession(String userId) {
        if (userId == null) {
            return Mono.error(new RuntimeException("UserId is required"));
        }

        return userRepository.findById(userId)
                .map(user -> true)
                .defaultIfEmpty(false);
    }
}
