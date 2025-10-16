package com.jingwook.mafia_server.controllers;

import com.jingwook.mafia_server.dtos.*;
import com.jingwook.mafia_server.services.GameService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("")
public class GameController {
    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    /**
     * 게임 시작
     * POST /api/rooms/{roomId}/games/start
     */
    @PostMapping("/rooms/{roomId}/games/start")
    public Mono<GameStateResponse> startGame(@PathVariable String roomId) {
        return gameService.startGame(roomId);
    }

    /**
     * 게임 상태 조회
     * GET /api/games/{gameId}
     */
    @GetMapping("/games/{gameId}")
    public Mono<GameStateResponse> getGameState(@PathVariable String gameId) {
        return gameService.getGameState(gameId);
    }

    /**
     * 내 직업 조회
     * GET /api/games/{gameId}/my-role?userId={userId}
     */
    @GetMapping("/games/{gameId}/my-role")
    public Mono<MyRoleResponse> getMyRole(
            @PathVariable String gameId,
            @RequestParam String userId) {
        return gameService.getMyRole(gameId, userId);
    }

    /**
     * 게임 참여자 목록
     * GET /api/games/{gameId}/players
     */
    @GetMapping("/games/{gameId}/players")
    public Mono<GamePlayersResponse> getPlayers(@PathVariable String gameId) {
        return gameService.getPlayers(gameId);
    }

    /**
     * 행동 등록 (투표, 마피아 살해, 의사 치료, 경찰 조사)
     * POST /api/games/{gameId}/actions
     */
    @PostMapping("/games/{gameId}/actions")
    public Mono<Void> registerAction(
            @PathVariable String gameId,
            @Valid @RequestBody RegisterActionDto body) {
        return gameService.registerAction(gameId, body);
    }

    /**
     * 투표 현황 조회
     * GET /api/games/{gameId}/votes?dayCount={dayCount}
     */
    @GetMapping("/games/{gameId}/votes")
    public Mono<VoteStatusResponse> getVoteStatus(
            @PathVariable String gameId,
            @RequestParam Integer dayCount) {
        return gameService.getVoteStatus(gameId, dayCount);
    }

    /**
     * 다음 페이즈로 전환
     * POST /api/games/{gameId}/next-phase
     */
    @PostMapping("/games/{gameId}/next-phase")
    public Mono<NextPhaseResponse> nextPhase(@PathVariable String gameId) {
        return gameService.nextPhase(gameId);
    }
}
