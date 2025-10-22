package com.jingwook.mafia_server.events;

import com.jingwook.mafia_server.enums.Team;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameEndedEventTest {

    @Test
    void 생성자로_이벤트를_생성한다() {
        // given & when
        GameEndedEvent event = new GameEndedEvent("room1", "game1", Team.MAFIA);

        // then
        assertEquals("room1", event.getRoomId());
        assertEquals("game1", event.getGameId());
        assertEquals(Team.MAFIA, event.getWinnerTeam());
    }

    @Test
    void CITIZEN_승리_이벤트를_생성한다() {
        // given & when
        GameEndedEvent event = new GameEndedEvent("room2", "game2", Team.CITIZEN);

        // then
        assertEquals("room2", event.getRoomId());
        assertEquals("game2", event.getGameId());
        assertEquals(Team.CITIZEN, event.getWinnerTeam());
    }

    @Test
    void DRAW_이벤트를_생성한다() {
        // given & when
        GameEndedEvent event = new GameEndedEvent("room3", "game3", Team.DRAW);

        // then
        assertEquals("room3", event.getRoomId());
        assertEquals("game3", event.getGameId());
        assertEquals(Team.DRAW, event.getWinnerTeam());
    }
}
