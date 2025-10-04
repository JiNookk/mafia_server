package com.jingwook.mafia_server.controllers;


import com.jingwook.mafia_server.dtos.SessionResponseDto;
import com.jingwook.mafia_server.dtos.SignupDto;
import com.jingwook.mafia_server.services.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private AuthService authService;

    @PostMapping("/signup")
    public Mono<ResponseEntity<SessionResponseDto>> signup(@RequestBody SignupDto body){
        return authService.signup(body.getNickname())
                .map(ResponseEntity::ok);
    }

}
