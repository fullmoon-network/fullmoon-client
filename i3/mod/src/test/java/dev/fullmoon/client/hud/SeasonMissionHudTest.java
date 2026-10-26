package dev.fullmoon.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import dev.fullmoon.client.network.BridgeState;
import dev.fullmoon.client.network.SeasonProtocol;

import org.junit.jupiter.api.Test;

final class SeasonMissionHudTest {
    private static final SeasonProtocol.Goal COAL =
        new SeasonProtocol.Goal("daily_coal", "석탄 캐기", "minecraft:coal", 13, 32, "개", false);
    private static final SeasonProtocol.Goal IRON =
        new SeasonProtocol.Goal("daily_iron", "철광석 캐기", "minecraft:raw_iron", 6, 16, "개", false);

    @Test
    void theWidgetGrowsALineForEachPassingEvent() {
        BridgeState.SeasonFeed quiet = feed(Optional.of(COAL), Optional.empty(), Optional.empty());
        BridgeState.SeasonFeed untracked = feed(Optional.empty(), Optional.empty(), Optional.empty());
        BridgeState.SeasonFeed busy = feed(Optional.of(COAL), Optional.of(IRON),
            Optional.of(new SeasonProtocol.Flash("daily_coal", "석탄 캐기 완료")));

        assertEquals(42, SeasonMissionHud.height(quiet, 1_000, false));
        assertEquals(34, SeasonMissionHud.height(untracked, 1_000, false));
        assertEquals(74, SeasonMissionHud.height(busy, 1_000, false));
        assertEquals(58, SeasonMissionHud.height(busy, 3_500, false), "the flash has gone, the recent line stays");
        assertEquals(42, SeasonMissionHud.height(busy, 4_000, false));
        assertEquals(42, SeasonMissionHud.height(busy, 1_000, true), "the editor places the resting widget");
    }

    @Test
    void aPassingLineFadesOverItsLastBeat() {
        assertEquals(1.0f, SeasonMissionHud.fade(0, 3_000));
        assertEquals(1.0f, SeasonMissionHud.fade(2_780, 3_000));
        assertEquals(0.5f, SeasonMissionHud.fade(2_890, 3_000));
        assertEquals(0.0f, SeasonMissionHud.fade(3_000, 3_000));
    }

    private static BridgeState.SeasonFeed feed(Optional<SeasonProtocol.Goal> tracked,
            Optional<SeasonProtocol.Goal> recent, Optional<SeasonProtocol.Flash> flash) {
        return new BridgeState.SeasonFeed(27, 95, 147, tracked, recent, 1_000, flash, 1_000);
    }
}
