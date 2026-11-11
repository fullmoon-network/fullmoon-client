package dev.fullmoon.client.season;

import dev.fullmoon.client.network.SeasonProtocol;

/**
 * What the pass may ask for, decided from the snapshot alone. The claim window is the season's
 * live phase and the grace after it; outside it a claim or a purchase is futile however a stale
 * or inconsistent claimable count reads, so the screen neither enables its controls nor sends the
 * action. Looking around — pages, tracked missions — stays open the whole time.
 */
final class SeasonGates {
    private SeasonGates() {}

    /** Whether the season's claim window is open: live, or the grace after it. */
    static boolean claimsOpen(SeasonProtocol.Open pass) {
        return switch (pass.season().phase()) {
            case LIVE, CLAIM -> true;
            default -> false;
        };
    }

    /** Whether the screen may put this action on the wire against this snapshot. */
    static boolean sends(SeasonProtocol.Open pass, SeasonProtocol.Action action) {
        boolean open = claimsOpen(pass);
        return switch (action) {
            case SeasonProtocol.Claim claim -> open;
            case SeasonProtocol.ClaimAll ignored -> open;
            case SeasonProtocol.BuyPlus ignored -> open;
            case SeasonProtocol.TrackMission track -> true;
            case SeasonProtocol.Untrack ignored -> true;
            case SeasonProtocol.SwitchTab tab -> true;
        };
    }
}
