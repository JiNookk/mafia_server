package com.jingwook.mafia_server.events;

import com.jingwook.mafia_server.dtos.NextPhaseResponse;
import com.jingwook.mafia_server.enums.GamePhase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PhaseChangedEventTest {

    @Test
    void 페이즈_변경_이벤트를_생성한다() {
        // given
        NextPhaseResponse.PhaseResult result = NextPhaseResponse.PhaseResult.builder()
                .deaths(List.of("player1"))
                .executedUserId(null)
                .winnerTeam(null)
                .build();

        NextPhaseResponse phaseData = NextPhaseResponse.builder()
                .currentPhase(GamePhase.DAY)
                .dayCount(1)
                .phaseStartTime(LocalDateTime.now())
                .phaseDurationSeconds(120)
                .lastPhaseResult(result)
                .build();

        // when
        PhaseChangedEvent event = new PhaseChangedEvent("room1", "game1", phaseData);

        // then
        assertEquals("room1", event.getRoomId());
        assertEquals("game1", event.getGameId());
        assertNotNull(event.getPhaseData());
        assertEquals(GamePhase.DAY, event.getPhaseData().getCurrentPhase());
        assertEquals(1, event.getPhaseData().getDayCount());
    }

    @Test
    void 페이즈_데이터를_포함한_이벤트를_생성한다() {
        // given
        NextPhaseResponse phaseData = NextPhaseResponse.builder()
                .currentPhase(GamePhase.VOTE)
                .dayCount(2)
                .phaseStartTime(LocalDateTime.now())
                .phaseDurationSeconds(30)
                .build();

        // when
        PhaseChangedEvent event = new PhaseChangedEvent("room2", "game2", phaseData);

        // then
        assertEquals("room2", event.getRoomId());
        assertEquals("game2", event.getGameId());
        assertEquals(phaseData, event.getPhaseData());
        assertEquals(GamePhase.VOTE, event.getPhaseData().getCurrentPhase());
        assertEquals(2, event.getPhaseData().getDayCount());
        assertEquals(30, event.getPhaseData().getPhaseDurationSeconds());
    }
}
