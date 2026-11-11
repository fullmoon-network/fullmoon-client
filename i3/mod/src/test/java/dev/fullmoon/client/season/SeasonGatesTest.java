package dev.fullmoon.client.season;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import dev.fullmoon.client.network.SeasonProtocol;

import org.junit.jupiter.api.Test;

/**
 * The phase gate on what the screen may send: a stale or inconsistent claimable flag must not
 * expose a claim or a purchase on an ended or upcoming season, while looking around stays open.
 */
final class SeasonGatesTest {
    @Test
    void aStaleClaimableCountCannotSendAClaimOnAnEndedSeason() {
        SeasonProtocol.Open ended = pass(SeasonProtocol.Phase.ENDED, 5);

        assertFalse(SeasonGates.claimsOpen(ended));
        assertFalse(SeasonGates.sends(ended, new SeasonProtocol.Claim(3, SeasonProtocol.Track.FREE)));
        assertFalse(SeasonGates.sends(ended, new SeasonProtocol.ClaimAll()));
        assertFalse(SeasonGates.sends(ended, new SeasonProtocol.BuyPlus()));
    }

    @Test
    void anUpcomingSeasonCannotExposeClaimOrPurchaseEither() {
        SeasonProtocol.Open upcoming = pass(SeasonProtocol.Phase.UPCOMING, 3);

        assertFalse(SeasonGates.claimsOpen(upcoming));
        assertFalse(SeasonGates.sends(upcoming, new SeasonProtocol.ClaimAll()));
        assertFalse(SeasonGates.sends(upcoming, new SeasonProtocol.BuyPlus()));
        assertFalse(SeasonGates.sends(upcoming,
            new SeasonProtocol.Claim(1, SeasonProtocol.Track.PLUS)));
    }

    @Test
    void theLiveAndClaimWindowsTakeClaimsAndPurchases() {
        SeasonProtocol.Open live = pass(SeasonProtocol.Phase.LIVE, 1);
        SeasonProtocol.Open claim = pass(SeasonProtocol.Phase.CLAIM, 1);

        assertTrue(SeasonGates.claimsOpen(live));
        assertTrue(SeasonGates.claimsOpen(claim));
        assertTrue(SeasonGates.sends(live, new SeasonProtocol.ClaimAll()));
        assertTrue(SeasonGates.sends(claim, new SeasonProtocol.Claim(2, SeasonProtocol.Track.FREE)));
        assertTrue(SeasonGates.sends(live, new SeasonProtocol.BuyPlus()));
    }

    @Test
    void lookingAroundStaysOpenWhateverThePhase() {
        for (SeasonProtocol.Phase phase : SeasonProtocol.Phase.values()) {
            SeasonProtocol.Open pass = pass(phase, 0);
            assertTrue(SeasonGates.sends(pass,
                new SeasonProtocol.TrackMission("daily_coal")), phase.name());
            assertTrue(SeasonGates.sends(pass, new SeasonProtocol.Untrack()), phase.name());
            assertTrue(SeasonGates.sends(pass,
                new SeasonProtocol.SwitchTab(SeasonProtocol.Tab.MISSIONS)), phase.name());
        }
    }

    /** A pass with the count a stale or careless producer might still send. */
    private static SeasonProtocol.Open pass(SeasonProtocol.Phase phase, int claimable) {
        return new SeasonProtocol.Open(1, "session-7", 4, SeasonProtocol.Tab.TRACK,
            new SeasonProtocol.Season("s1", "Season 1", phase, "2 days", ""),
            "XP",
            new SeasonProtocol.Me(3, 5, 95, 12, 17, false, claimable, Optional.of("50만원")),
            List.of(new SeasonProtocol.Level(1, 0,
                Optional.of(reward(SeasonProtocol.Status.CLAIMABLE)), Optional.empty())),
            new SeasonProtocol.Missions(List.of(), List.of(), List.of(), Optional.empty()),
            new SeasonProtocol.Plus(false, "40만원", true, false, List.of(), Optional.empty()),
            Optional.empty());
    }

    private static SeasonProtocol.Reward reward(SeasonProtocol.Status status) {
        return new SeasonProtocol.Reward(SeasonProtocol.Kind.WON, "5천원", "minecraft:gold_nugget",
            status, "", "", "");
    }
}
