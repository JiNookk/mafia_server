package com.jingwook.mafia_server.domains;

import com.jingwook.mafia_server.enums.GamePhase;
import com.jingwook.mafia_server.enums.Team;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GameTest {

    @Test
    void isFinished_게임이_종료되지_않았으면_false를_반환한다() {
        // given
        Game game = new Game(
                "game1",
                "room1",
                GamePhase.NIGHT,
                1,
                LocalDateTime.now(),
                60,
                null,
                LocalDateTime.now(),
                null
        );

        // when & then
        assertFalse(game.isFinished());
    }

    @Test
    void isFinished_게임이_종료되었으면_true를_반환한다() {
        // given
        Game game = new Game(
                "game1",
                "room1",
                GamePhase.NIGHT,
                1,
                LocalDateTime.now(),
                60,
                Team.MAFIA,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        // when & then
        assertTrue(game.isFinished());
    }

    @Test
    void isPhaseExpired_페이즈_시작시간이_null이면_false를_반환한다() {
        // given
        Game game = new Game(
                "game1",
                "room1",
                GamePhase.NIGHT,
                1,
                null,
                60,
                null,
                LocalDateTime.now(),
                null
        );

        // when & then
        assertFalse(game.isPhaseExpired());
    }

    @Test
    void isPhaseExpired_페이즈_지속시간이_null이면_false를_반환한다() {
        // given
        Game game = new Game(
                "game1",
                "room1",
                GamePhase.NIGHT,
                1,
                LocalDateTime.now(),
                null,
                null,
                LocalDateTime.now(),
                null
        );

        // when & then
        assertFalse(game.isPhaseExpired());
    }

    @Test
    void isPhaseExpired_페이즈_시간이_만료되지_않았으면_false를_반환한다() {
        // given
        Game game = new Game(
                "game1",
                "room1",
                GamePhase.NIGHT,
                1,
                LocalDateTime.now(),
                60,
                null,
                LocalDateTime.now(),
                null
        );

        // when & then
        assertFalse(game.isPhaseExpired());
    }

    @Test
    void isPhaseExpired_페이즈_시간이_만료되었으면_true를_반환한다() {
        // given
        Game game = new Game(
                "game1",
                "room1",
                GamePhase.NIGHT,
                1,
                LocalDateTime.now().minusSeconds(61),
                60,
                null,
                LocalDateTime.now(),
                null
        );

        // when & then
        assertTrue(game.isPhaseExpired());
    }

    @Test
    void transitionToNextPhase_NIGHT에서_DAY로_전환한다() {
        // given
        LocalDateTime startTime = LocalDateTime.now();
        Game game = new Game(
                "game1",
                "room1",
                GamePhase.NIGHT,
                1,
                startTime,
                60,
                null,
                LocalDateTime.now(),
                null
        );
        Map<GamePhase, Integer> phaseDurations = Map.of(
                GamePhase.DAY, 120,
                GamePhase.NIGHT, 60,
                GamePhase.VOTE, 30,
                GamePhase.DEFENSE, 20,
                GamePhase.RESULT, 10
        );

        // when
        Game nextGame = game.transitionToNextPhase(phaseDurations);

        // then
        assertEquals(GamePhase.DAY, nextGame.getCurrentPhase());
        assertEquals(120, nextGame.getPhaseDurationSeconds());
        assertEquals(1, nextGame.getDayCount());
        assertNotNull(nextGame.getPhaseStartTime());
        assertTrue(nextGame.getPhaseStartTime().isAfter(startTime));
    }

    @Test
    void transitionToNextPhase_DAY에서_VOTE로_전환한다() {
        // given
        Game game = new Game(
                "game1",
                "room1",
                GamePhase.DAY,
                1,
                LocalDateTime.now(),
                120,
                null,
                LocalDateTime.now(),
                null
        );
        Map<GamePhase, Integer> phaseDurations = Map.of(
                GamePhase.DAY, 120,
                GamePhase.NIGHT, 60,
                GamePhase.VOTE, 30,
                GamePhase.DEFENSE, 20,
                GamePhase.RESULT, 10
        );

        // when
        Game nextGame = game.transitionToNextPhase(phaseDurations);

        // then
        assertEquals(GamePhase.VOTE, nextGame.getCurrentPhase());
        assertEquals(30, nextGame.getPhaseDurationSeconds());
        assertEquals(1, nextGame.getDayCount());
    }

    @Test
    void transitionToNextPhase_VOTE에서_DEFENSE로_전환한다() {
        // given
        Game game = new Game(
                "game1",
                "room1",
                GamePhase.VOTE,
                1,
                LocalDateTime.now(),
                30,
                null,
                LocalDateTime.now(),
                null
        );
        Map<GamePhase, Integer> phaseDurations = Map.of(
                GamePhase.DAY, 120,
                GamePhase.NIGHT, 60,
                GamePhase.VOTE, 30,
                GamePhase.DEFENSE, 20,
                GamePhase.RESULT, 10
        );

        // when
        Game nextGame = game.transitionToNextPhase(phaseDurations);

        // then
        assertEquals(GamePhase.DEFENSE, nextGame.getCurrentPhase());
        assertEquals(20, nextGame.getPhaseDurationSeconds());
    }

    @Test
    void transitionToNextPhase_DEFENSE에서_RESULT로_전환한다() {
        // given
        Game game = new Game(
                "game1",
                "room1",
                GamePhase.DEFENSE,
                1,
                LocalDateTime.now(),
                20,
                null,
                LocalDateTime.now(),
                null
        );
        Map<GamePhase, Integer> phaseDurations = Map.of(
                GamePhase.DAY, 120,
                GamePhase.NIGHT, 60,
                GamePhase.VOTE, 30,
                GamePhase.DEFENSE, 20,
                GamePhase.RESULT, 10
        );

        // when
        Game nextGame = game.transitionToNextPhase(phaseDurations);

        // then
        assertEquals(GamePhase.RESULT, nextGame.getCurrentPhase());
        assertEquals(10, nextGame.getPhaseDurationSeconds());
    }

    @Test
    void transitionToNextPhase_RESULT에서_NIGHT로_전환하고_dayCount를_증가시킨다() {
        // given
        Game game = new Game(
                "game1",
                "room1",
                GamePhase.RESULT,
                1,
                LocalDateTime.now(),
                10,
                null,
                LocalDateTime.now(),
                null
        );
        Map<GamePhase, Integer> phaseDurations = Map.of(
                GamePhase.DAY, 120,
                GamePhase.NIGHT, 60,
                GamePhase.VOTE, 30,
                GamePhase.DEFENSE, 20,
                GamePhase.RESULT, 10
        );

        // when
        Game nextGame = game.transitionToNextPhase(phaseDurations);

        // then
        assertEquals(GamePhase.NIGHT, nextGame.getCurrentPhase());
        assertEquals(60, nextGame.getPhaseDurationSeconds());
        assertEquals(2, nextGame.getDayCount());
    }

    @Test
    void transitionToNextPhase_원본_게임_객체는_변경되지_않는다() {
        // given
        Game game = new Game(
                "game1",
                "room1",
                GamePhase.NIGHT,
                1,
                LocalDateTime.now(),
                60,
                null,
                LocalDateTime.now(),
                null
        );
        Map<GamePhase, Integer> phaseDurations = Map.of(
                GamePhase.DAY, 120,
                GamePhase.NIGHT, 60,
                GamePhase.VOTE, 30,
                GamePhase.DEFENSE, 20,
                GamePhase.RESULT, 10
        );

        // when
        Game nextGame = game.transitionToNextPhase(phaseDurations);

        // then
        assertEquals(GamePhase.NIGHT, game.getCurrentPhase());
        assertEquals(GamePhase.DAY, nextGame.getCurrentPhase());
        assertNotSame(game, nextGame);
    }

    @Test
    void endGame_게임을_종료하고_승자를_설정한다() {
        // given
        LocalDateTime beforeEnd = LocalDateTime.now();
        Game game = new Game(
                "game1",
                "room1",
                GamePhase.NIGHT,
                1,
                LocalDateTime.now(),
                60,
                null,
                LocalDateTime.now(),
                null
        );

        // when
        Game endedGame = game.endGame(Team.MAFIA);

        // then
        assertEquals(Team.MAFIA, endedGame.getWinnerTeam());
        assertNotNull(endedGame.getFinishedAt());
        assertTrue(endedGame.getFinishedAt().isAfter(beforeEnd));
        assertTrue(endedGame.isFinished());
    }

    @Test
    void endGame_원본_게임_객체는_변경되지_않는다() {
        // given
        Game game = new Game(
                "game1",
                "room1",
                GamePhase.NIGHT,
                1,
                LocalDateTime.now(),
                60,
                null,
                LocalDateTime.now(),
                null
        );

        // when
        Game endedGame = game.endGame(Team.CITIZEN);

        // then
        assertFalse(game.isFinished());
        assertTrue(endedGame.isFinished());
        assertNull(game.getWinnerTeam());
        assertEquals(Team.CITIZEN, endedGame.getWinnerTeam());
        assertNotSame(game, endedGame);
    }

    @Test
    void endGame_다른_필드는_유지된다() {
        // given
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(10);
        LocalDateTime phaseStartTime = LocalDateTime.now().minusMinutes(1);
        Game game = new Game(
                "game1",
                "room1",
                GamePhase.VOTE,
                3,
                phaseStartTime,
                30,
                null,
                startTime,
                null
        );

        // when
        Game endedGame = game.endGame(Team.DRAW);

        // then
        assertEquals("game1", endedGame.getId());
        assertEquals("room1", endedGame.getRoomId());
        assertEquals(GamePhase.VOTE, endedGame.getCurrentPhase());
        assertEquals(3, endedGame.getDayCount());
        assertEquals(phaseStartTime, endedGame.getPhaseStartTime());
        assertEquals(30, endedGame.getPhaseDurationSeconds());
        assertEquals(startTime, endedGame.getStartedAt());
    }
}
