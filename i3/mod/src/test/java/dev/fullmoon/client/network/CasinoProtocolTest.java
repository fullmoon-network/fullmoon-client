package dev.fullmoon.client.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

final class CasinoProtocolTest {
    @Test
    void eachGameDecodesItsDetail() {
        CasinoProtocol.Result dice = result("""
            {"type":"casino_result","game":"dice","won":true,"payout_multiplier":1.98,
             "detail":{"roll":42,"target":50}}
            """);
        CasinoProtocol.Result roulette = result("""
            {"type":"casino_result","game":"roulette","won":false,"payout_multiplier":0,
             "detail":{"pocket":0,"bet_type":"straight:17"}}
            """);
        CasinoProtocol.Result slots = result("""
            {"type":"casino_result","game":"slots","won":true,"payout_multiplier":12,
             "detail":{"reels":["moon","moon","bell"],"matched":2}}
            """);
        CasinoProtocol.Result coin = result("""
            {"type":"casino_result","game":"coinflip","won":false,"payout_multiplier":0}
            """);

        assertEquals(new CasinoProtocol.Roll(42, 50), dice.detail());
        assertEquals(1.98, dice.payoutMultiplier());
        assertEquals(new CasinoProtocol.Spin(0, "straight:17"), roulette.detail());
        assertEquals(new CasinoProtocol.Reels(List.of("moon", "moon", "bell"), 2), slots.detail());
        assertInstanceOf(CasinoProtocol.Coin.class, coin.detail());
        assertEquals(CasinoProtocol.Game.COINFLIP, coin.game());
    }

    @Test
    void aMissingProtoIsTheCurrentVersion() {
        assertEquals(BridgeProtocol.VERSION, result("""
            {"type":"casino_result","game":"coinflip","won":true,"payout_multiplier":2}
            """).proto());
    }

    @Test
    void theEnvelopeIsValidatedBeforeTheDetail() {
        assertEquals("casino_result game is required", error("""
            {"type":"casino_result","won":true,"payout_multiplier":2}
            """));
        assertEquals("casino_result game is not supported: blackjack", error("""
            {"type":"casino_result","game":"blackjack","won":true,"payout_multiplier":2}
            """));
        assertEquals("casino_result won must be a boolean", error("""
            {"type":"casino_result","game":"coinflip","won":"yes","payout_multiplier":2}
            """));
        assertEquals("casino_result payout_multiplier must be between 0 and 1000", error("""
            {"type":"casino_result","game":"coinflip","won":true,"payout_multiplier":1001}
            """));
        assertEquals("casino_result payout_multiplier must be between 0 and 1000", error("""
            {"type":"casino_result","game":"coinflip","won":false,"payout_multiplier":-1}
            """));
        assertEquals("casino_result payout_multiplier must be positive when won", error("""
            {"type":"casino_result","game":"coinflip","won":true,"payout_multiplier":0}
            """));
        assertEquals("casino_result proto must be a non-negative integer", error("""
            {"type":"casino_result","proto":-1,"game":"coinflip","won":false,"payout_multiplier":0}
            """));
    }

    @Test
    void detailRangesMatchTheServersGames() {
        assertEquals("casino_result dice roll must be between 0 and 99", error("""
            {"type":"casino_result","game":"dice","won":false,"payout_multiplier":0,
             "detail":{"roll":100,"target":50}}
            """));
        assertEquals("casino_result dice target must be between 0 and 100", error("""
            {"type":"casino_result","game":"dice","won":false,"payout_multiplier":0,
             "detail":{"roll":3,"target":101}}
            """));
        assertEquals("casino_result dice roll must be between 0 and 99", error("""
            {"type":"casino_result","game":"dice","won":false,"payout_multiplier":0}
            """));
        assertEquals("casino_result roulette pocket must be between 0 and 36", error("""
            {"type":"casino_result","game":"roulette","won":false,"payout_multiplier":0,
             "detail":{"pocket":37,"bet_type":"red"}}
            """));
        assertEquals("casino_result roulette bet_type is required", error("""
            {"type":"casino_result","game":"roulette","won":false,"payout_multiplier":0,
             "detail":{"pocket":7,"bet_type":" "}}
            """));
        assertEquals("casino_result roulette bet_type is too long", error("""
            {"type":"casino_result","game":"roulette","won":false,"payout_multiplier":0,
             "detail":{"pocket":7,"bet_type":"%s"}}
            """.formatted("x".repeat(33))));
    }

    @Test
    void reelsAreBoundedAndMatchedFitsThem() {
        assertEquals("casino_result slots reels must hold 1 to 5 symbols", error("""
            {"type":"casino_result","game":"slots","won":false,"payout_multiplier":0,
             "detail":{"reels":[],"matched":1}}
            """));
        assertEquals("casino_result slots reels must hold 1 to 5 symbols", error("""
            {"type":"casino_result","game":"slots","won":false,"payout_multiplier":0,
             "detail":{"reels":["a","b","c","d","e","f"],"matched":1}}
            """));
        assertEquals("casino_result slots symbol is invalid", error("""
            {"type":"casino_result","game":"slots","won":false,"payout_multiplier":0,
             "detail":{"reels":["Moon","bell","star"],"matched":1}}
            """));
        assertEquals("casino_result slots matched must be between 1 and the reel count", error("""
            {"type":"casino_result","game":"slots","won":false,"payout_multiplier":0,
             "detail":{"reels":["moon","bell","star"],"matched":4}}
            """));
        assertEquals("casino_result slots reels must be an array", error("""
            {"type":"casino_result","game":"slots","won":false,"payout_multiplier":0,
             "detail":{"reels":"moon","matched":1}}
            """));
    }

    private static CasinoProtocol.Result result(String json) {
        return assertInstanceOf(CasinoProtocol.Result.class,
            decode(json).message().orElseThrow(() -> new AssertionError(decode(json).error())));
    }

    private static String error(String json) {
        return decode(json).error().orElseThrow();
    }

    private static BridgeProtocol.DecodeResult decode(String json) {
        return BridgeProtocol.decode(json.getBytes(StandardCharsets.UTF_8));
    }
}
