package dev.fullmoon.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.network.BridgeProtocol;
import dev.fullmoon.client.network.BridgeState;
import dev.fullmoon.client.network.CasinoProtocol;

import org.junit.jupiter.api.Test;

final class CasinoResultOverlayTest {
    @Test
    void theCardRisesHoldsAndSinksWithinItsLifetime() {
        assertEquals(1f, CasinoResultOverlay.hidden(0));
        assertEquals(0f, CasinoResultOverlay.hidden(CasinoResultOverlay.ENTER));
        assertEquals(0f, CasinoResultOverlay.hidden(CasinoResultOverlay.LEAVE));
        assertEquals(1f, CasinoResultOverlay.hidden(BridgeState.CASINO_CARD_MILLIS));
        assertTrue(CasinoResultOverlay.SETTLE < CasinoResultOverlay.LEAVE);
    }

    @Test
    void theLastReelLandsAsTheRevealSettles() {
        assertEquals(CasinoResultOverlay.SETTLE, CasinoResultOverlay.reelStop(2, 3));
        assertEquals(CasinoResultOverlay.SETTLE - 2L * Tokens.Duration.SLOW,
            CasinoResultOverlay.reelStop(0, 3));
        assertTrue(CasinoResultOverlay.reelStop(0, 5) > CasinoResultOverlay.ENTER);
    }

    @Test
    void betsReadAsThePlayerPlacedThem() {
        assertEquals("빨강", CasinoResultOverlay.betName("red"));
        assertEquals("19-36", CasinoResultOverlay.betName("high"));
        assertEquals("숫자 17", CasinoResultOverlay.betName("straight:17"));
        assertEquals("2열", CasinoResultOverlay.betName("column:2"));
        assertEquals("3번째 12", CasinoResultOverlay.betName("dozen:3"));
        assertEquals("split:1-2", CasinoResultOverlay.betName("split:1-2"));
        assertEquals("straight:", CasinoResultOverlay.betName("straight:"));
    }

    @Test
    void pocketsTakeTheirWheelColour() {
        assertEquals(Tokens.Color.STATUS_LIVE, CasinoResultOverlay.pocketColor(0));
        assertEquals(Tokens.Color.STATUS_DANGER, CasinoResultOverlay.pocketColor(32));
        assertEquals(Tokens.Color.SURFACE_RAISED, CasinoResultOverlay.pocketColor(15));
    }

    @Test
    void theMultiplierDropsATrailingZero() {
        assertEquals("×12", CasinoResultOverlay.multiplier(12.0));
        assertEquals("×1.98", CasinoResultOverlay.multiplier(1.98));
        assertEquals("×2.5", CasinoResultOverlay.multiplier(2.50));
    }

    @Test
    void copyWaitsForTheRevealBeforeNamingTheOutcome() {
        CasinoProtocol.Result dice = result(CasinoProtocol.Game.DICE, true, new CasinoProtocol.Roll(42, 50));
        CasinoProtocol.Result wheel = result(CasinoProtocol.Game.ROULETTE, false,
            new CasinoProtocol.Spin(17, "red"));
        CasinoProtocol.Result reels = result(CasinoProtocol.Game.SLOTS, true,
            new CasinoProtocol.Reels(List.of("bell", "moon", "moon"), 2));

        assertEquals("주사위를 굴리고 있어요", CasinoResultOverlay.title(dice, false));
        assertEquals("당첨이에요", CasinoResultOverlay.title(dice, true));
        assertEquals("아쉽지만 다음 기회예요", CasinoResultOverlay.title(wheel, true));
        assertEquals("목표 50 미만이 나오면 이겨요", CasinoResultOverlay.detail(dice, false));
        assertEquals("굴림 42 · 목표 50 미만", CasinoResultOverlay.detail(dice, true));
        assertEquals("빨강에 걸었어요", CasinoResultOverlay.detail(wheel, false));
        assertEquals("포켓 17 · 빨강에 걸었어요", CasinoResultOverlay.detail(wheel, true));
        assertEquals("", CasinoResultOverlay.detail(reels, false));
        assertEquals("만월 2개가 맞았어요", CasinoResultOverlay.detail(reels, true));
        assertEquals("같은 그림이 없어요", CasinoResultOverlay.detail(
            result(CasinoProtocol.Game.SLOTS, false,
                new CasinoProtocol.Reels(List.of("bell", "moon", "star"), 1)), true));
    }

    private static CasinoProtocol.Result result(
            CasinoProtocol.Game game, boolean won, CasinoProtocol.Detail detail) {
        return new CasinoProtocol.Result(BridgeProtocol.VERSION, game, won, won ? 2 : 0, detail);
    }
}
