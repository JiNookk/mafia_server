package com.jingwook.mafia_server.controllers;

import com.jingwook.mafia_server.dtos.GetRoomListQueryDto;
import com.jingwook.mafia_server.dtos.OffsetPaginationDto;
import com.jingwook.mafia_server.dtos.RoomListResponse;
import com.jingwook.mafia_server.services.RoomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/rooms")
public class RoomController {
    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public Mono<OffsetPaginationDto<RoomListResponse>> getList(
            GetRoomListQueryDto query
    ){
        return roomService.getList(query);
    }
}
