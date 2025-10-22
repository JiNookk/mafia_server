package com.jingwook.mafia_server.events;

import com.jingwook.mafia_server.enums.DeathReason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerDiedEventTest {

    @Test
    void 단일_플레이어_사망_이벤트를_생성한다() {
        // given & when
        PlayerDiedEvent event = new PlayerDiedEvent(
                "room1",
                "game1",
                List.of("player1"),
                DeathReason.KILLED
        );

        // then
        assertEquals("room1", event.getRoomId());
        assertEquals("game1", event.getGameId());
        assertEquals(List.of("player1"), event.getDeadPlayerIds());
        assertEquals(DeathReason.KILLED, event.getReason());
    }

    @Test
    void 여러_플레이어_사망_이벤트를_생성한다() {
        // given & when
        PlayerDiedEvent event = new PlayerDiedEvent(
                "room2",
                "game2",
                List.of("player1", "player2", "player3"),
                DeathReason.EXECUTED
        );

        // then
        assertEquals("room2", event.getRoomId());
        assertEquals("game2", event.getGameId());
        assertEquals(3, event.getDeadPlayerIds().size());
        assertTrue(event.getDeadPlayerIds().contains("player1"));
        assertTrue(event.getDeadPlayerIds().contains("player2"));
        assertTrue(event.getDeadPlayerIds().contains("player3"));
        assertEquals(DeathReason.EXECUTED, event.getReason());
    }

    @Test
    void 빈_사망자_리스트로_이벤트를_생성한다() {
        // given & when
        PlayerDiedEvent event = new PlayerDiedEvent(
                "room3",
                "game3",
                List.of(),
                DeathReason.DOCTOR_FAILED
        );

        // then
        assertEquals("room3", event.getRoomId());
        assertEquals("game3", event.getGameId());
        assertTrue(event.getDeadPlayerIds().isEmpty());
        assertEquals(DeathReason.DOCTOR_FAILED, event.getReason());
    }
}
