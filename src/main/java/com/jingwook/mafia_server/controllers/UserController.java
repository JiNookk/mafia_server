package com.jingwook.mafia_server.controllers;


import com.jingwook.mafia_server.dtos.SessionResponseDto;
import com.jingwook.mafia_server.dtos.SignupDto;
import com.jingwook.mafia_server.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/users")
public class UserController {
    private UserService userService;

    @PostMapping("/join")
    public Mono<ResponseEntity<SessionResponseDto>> join(@RequestBody SignupDto body){
        return userService.joinUser(body.getNickname())
                .map(ResponseEntity::ok);
    }

}
