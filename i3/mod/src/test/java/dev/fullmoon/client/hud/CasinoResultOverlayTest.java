package dev.fullmoon.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.network.BridgeProtocol;
import dev.fullmoon.client.network.BridgeState;
import dev.fullmoon.client.network.CasinoProtocol;
import dev.fullmoon.client.sound.UiSounds;

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
    void theFlashPeaksAtTheVerdictAndIsGoneInItsDuration() {
        assertEquals(0f, CasinoResultOverlay.flash(CasinoResultOverlay.SETTLE - 1, true));
        assertEquals(0.4f, CasinoResultOverlay.flash(CasinoResultOverlay.SETTLE, true), 1e-6);
        assertTrue(CasinoResultOverlay.flash(CasinoResultOverlay.SETTLE + 100, true) < 0.4f);
        assertEquals(0f, CasinoResultOverlay.flash(CasinoResultOverlay.SETTLE + Tokens.Duration.FLASH, true));
        assertEquals(0f, CasinoResultOverlay.flash(CasinoResultOverlay.SETTLE, false));
    }

    @Test
    void cuesFallDueAsReelsLandAndTheVerdictArrives() {
        CasinoProtocol.Result reels = result(CasinoProtocol.Game.SLOTS, true,
            new CasinoProtocol.Reels(List.of("moon", "moon", "moon"), 3));
        assertEquals(List.of(), CasinoResultOverlay.cuesDue(reels, CasinoResultOverlay.ENTER));
        assertEquals(List.of(UiSounds.Cue.REEL),
            CasinoResultOverlay.cuesDue(reels, CasinoResultOverlay.reelStop(0, 3)));
        assertEquals(List.of(UiSounds.Cue.REEL, UiSounds.Cue.REEL),
            CasinoResultOverlay.cuesDue(reels, CasinoResultOverlay.reelStop(1, 3)));
        assertEquals(List.of(UiSounds.Cue.REEL, UiSounds.Cue.REEL, UiSounds.Cue.REEL, UiSounds.Cue.WIN),
            CasinoResultOverlay.cuesDue(reels, CasinoResultOverlay.SETTLE));

        CasinoProtocol.Result coin = result(CasinoProtocol.Game.COINFLIP, false, new CasinoProtocol.Coin());
        assertEquals(List.of(), CasinoResultOverlay.cuesDue(coin, CasinoResultOverlay.SETTLE - 1));
        assertEquals(List.of(UiSounds.Cue.LOSE), CasinoResultOverlay.cuesDue(coin, CasinoResultOverlay.SETTLE));
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
        assertEquals(Tokens.Color.WHEEL_GREEN, CasinoResultOverlay.pocketColor(0));
        assertEquals(Tokens.Color.WHEEL_RED, CasinoResultOverlay.pocketColor(32));
        assertEquals(Tokens.Color.WHEEL_BLACK, CasinoResultOverlay.pocketColor(15));
    }

    @Test
    void theMultiplierDropsATrailingZero() {
        assertEquals("12배", CasinoResultOverlay.multiplier(12.0));
        assertEquals("1.98배", CasinoResultOverlay.multiplier(1.98));
        assertEquals("2.5배", CasinoResultOverlay.multiplier(2.50));
        assertEquals("1.96배", CasinoResultOverlay.multiplier(1.9607843137254901));
        assertEquals("12배", CasinoResultOverlay.multiplier(12.004));
    }

    @Test
    void copyWaitsForTheRevealBeforeNamingTheOutcome() {
        CasinoProtocol.Result dice = result(CasinoProtocol.Game.DICE, true, new CasinoProtocol.Roll(42, 50));
        CasinoProtocol.Result wheel = result(CasinoProtocol.Game.ROULETTE, false,
            new CasinoProtocol.Spin(17, "red"));
        CasinoProtocol.Result reels = result(CasinoProtocol.Game.SLOTS, true,
            new CasinoProtocol.Reels(List.of("bell", "moon", "moon"), 2));
        CasinoProtocol.Result coin = result(CasinoProtocol.Game.COINFLIP, true, new CasinoProtocol.Coin());

        assertEquals("주사위가 굴러요", CasinoResultOverlay.title(dice, false));
        assertEquals("당첨", CasinoResultOverlay.title(dice, true));
        assertEquals("아쉬워요", CasinoResultOverlay.title(wheel, true));

        assertEquals("주사위 · 목표 50 미만", CasinoResultOverlay.detail(dice, false));
        assertEquals("", CasinoResultOverlay.meta(dice, false));
        assertEquals("나온 수 42", CasinoResultOverlay.meta(dice, true));
        assertEquals("2배", CasinoResultOverlay.figure(dice, true));

        assertEquals("룰렛 · 빨강에 걸었어요", CasinoResultOverlay.detail(wheel, true));
        assertEquals("포켓 17", CasinoResultOverlay.meta(wheel, true));
        assertEquals("17", CasinoResultOverlay.figure(wheel, true), "a loss shows what came up, not a multiplier");
        assertEquals("", CasinoResultOverlay.figure(wheel, false));

        assertEquals("슬롯", CasinoResultOverlay.detail(reels, false));
        assertEquals("슬롯 · 종 · 만월 · 만월", CasinoResultOverlay.detail(reels, true));
        assertEquals("2개 일치", CasinoResultOverlay.meta(reels, true));
        assertEquals("일치 없음", CasinoResultOverlay.meta(
            result(CasinoProtocol.Game.SLOTS, false,
                new CasinoProtocol.Reels(List.of("bell", "moon", "star"), 1)), true));

        assertEquals("동전 던지기", CasinoResultOverlay.detail(coin, true));
        assertEquals("고른 면", CasinoResultOverlay.meta(coin, true));
        assertEquals("반대 면", CasinoResultOverlay.meta(
            result(CasinoProtocol.Game.COINFLIP, false, new CasinoProtocol.Coin()), true));
    }

    private static CasinoProtocol.Result result(
            CasinoProtocol.Game game, boolean won, CasinoProtocol.Detail detail) {
        return new CasinoProtocol.Result(BridgeProtocol.VERSION, game, won, won ? 2 : 0, detail);
    }
}
