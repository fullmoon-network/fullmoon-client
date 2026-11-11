package dev.fullmoon.client.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.fullmoon.client.network.SeasonProtocol;

import org.junit.jupiter.api.Test;

final class SeasonCopyTest {
    @Test
    void theHeaderPresentsTheServersCountdownUnchanged() {
        // The producer names the phase inside endsIn itself; the client adds nothing to it.
        assertEquals("3 days", SeasonCopy.context(season(SeasonProtocol.Phase.UPCOMING, "3 days")));
        assertEquals("17 days left", SeasonCopy.context(season(SeasonProtocol.Phase.LIVE, "17 days left")));
        assertEquals("2 days left", SeasonCopy.context(season(SeasonProtocol.Phase.CLAIM, "2 days left")));
        assertEquals("Season ended", SeasonCopy.context(season(SeasonProtocol.Phase.ENDED, "2 days left")));
        assertEquals("", SeasonCopy.context(season(SeasonProtocol.Phase.LIVE, "")));
    }

    @Test
    void aCellSaysWhichOfTheServersStatesItIsIn() {
        SeasonProtocol.Phase live = SeasonProtocol.Phase.LIVE;
        assertEquals("Claimed", SeasonCopy.status(reward(SeasonProtocol.Status.CLAIMED), 3, 0, "XP", live));
        assertEquals("Ready to claim", SeasonCopy.status(reward(SeasonProtocol.Status.CLAIMABLE), 3, 0, "XP", live));
        assertEquals("Unlocks at level 30 · 1,204 XP to go",
            SeasonCopy.status(reward(SeasonProtocol.Status.LOCKED), 30, 1_204, "XP", live));
        assertEquals("Unlocks at level 30",
            SeasonCopy.status(reward(SeasonProtocol.Status.LOCKED), 30, 0, "XP", live));
        assertEquals("Plus unlocks this reward",
            SeasonCopy.status(reward(SeasonProtocol.Status.PLUS_LOCKED), 3, 0, "XP", live));
        assertEquals("Daily limit reached; claimable when it resets",
            SeasonCopy.status(reward(SeasonProtocol.Status.PENDING), 3, 0, "XP", live));
    }

    @Test
    void afterTheClaimWindowALockedCellIsClosedByTheCalendar() {
        SeasonProtocol.Phase ended = SeasonProtocol.Phase.ENDED;
        assertEquals("Claim window closed",
            SeasonCopy.status(reward(SeasonProtocol.Status.LOCKED), 30, 1_204, "XP", ended));
        assertEquals("Claim window closed",
            SeasonCopy.status(reward(SeasonProtocol.Status.LOCKED), 3, 0, "XP", ended));
        assertEquals("Claim window closed",
            SeasonCopy.status(reward(SeasonProtocol.Status.PLUS_LOCKED), 3, 0, "XP", ended));
        assertEquals("Claimed", SeasonCopy.status(reward(SeasonProtocol.Status.CLAIMED), 3, 0, "XP", ended));
        // Even a stale claimable flag cannot advertise a claim the window cannot honour.
        assertEquals("Claim window closed",
            SeasonCopy.status(reward(SeasonProtocol.Status.CLAIMABLE), 3, 0, "XP", ended));
        assertEquals("Claim window closed",
            SeasonCopy.status(reward(SeasonProtocol.Status.PENDING), 3, 0, "XP", ended));
    }

    @Test
    void figuresAreGroupedAndThePointIsTheServersWord() {
        assertEquals("1,530 / 6,000 units", SeasonCopy.count(1_530, 6_000, " units"));
        assertEquals("+40 Moonlight", SeasonCopy.reward(40, "Moonlight"));
        assertEquals("95 / 147 XP", SeasonCopy.toNext(95, 147, "XP"));
        assertEquals("Stage 1 / 3", SeasonCopy.stage(0, 3));
        assertEquals("Stage 3 / 3", SeasonCopy.stage(3, 3));
    }

    @Test
    void rewardKindsAndTabsHaveTheirWords() {
        assertEquals("Currency", SeasonCopy.kind(reward(SeasonProtocol.Status.CLAIMED)));
        assertEquals("5,000 units", SeasonCopy.shortKind(reward(SeasonProtocol.Status.CLAIMED)));
        SeasonProtocol.Reward skin = new SeasonProtocol.Reward(SeasonProtocol.Kind.COSMETIC, "Moonlight Pickaxe",
            "minecraft:iron_pickaxe", SeasonProtocol.Status.LOCKED, "", "moonlight_pickaxe",
            "fullmoon.skin.moonlight_pickaxe.1");
        assertEquals("Growing cosmetic", SeasonCopy.kind(skin));
        assertEquals("Cosmetic", SeasonCopy.shortKind(skin));
        assertEquals("Missions", SeasonCopy.tab(SeasonProtocol.Tab.MISSIONS));
        assertEquals("Plus", SeasonCopy.track(SeasonProtocol.Track.PLUS));
    }

    @Test
    void anUntrackableMissionSaysWhereItMoves() {
        assertEquals("", SeasonCopy.untrackable(mission(false, false, true, false), false));
        assertEquals("", SeasonCopy.untrackable(mission(true, false, false, false), false));
        assertEquals("Available with Plus", SeasonCopy.untrackable(mission(false, true, false, false), false));
        assertEquals("Runs in Discord · link your Minecraft account",
            SeasonCopy.untrackable(mission(false, false, false, true), true));
        assertEquals("Progresses in the wild", SeasonCopy.untrackable(mission(false, true, false, false), true));
    }

    @Test
    void aSeasonWithoutAPhaseIsRejectedAtConstruction() {
        assertThrows(NullPointerException.class,
            () -> new SeasonProtocol.Season("s1", "Season 1", null, "", ""));
    }

    private static SeasonProtocol.Season season(SeasonProtocol.Phase phase, String endsIn) {
        return new SeasonProtocol.Season("s1", "Season 1", phase, endsIn, "");
    }

    private static SeasonProtocol.Reward reward(SeasonProtocol.Status status) {
        return new SeasonProtocol.Reward(SeasonProtocol.Kind.WON, "5,000 units", "minecraft:gold_nugget",
            status, "", "", "");
    }

    private static SeasonProtocol.Mission mission(boolean done, boolean plusExtra, boolean trackable, boolean discord) {
        return new SeasonProtocol.Mission("m", "Mission", "minecraft:coal", 1, 10, " units", 30, 0, 1, done,
            plusExtra, false, trackable, discord);
    }
}
