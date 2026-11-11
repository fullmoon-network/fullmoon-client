package dev.fullmoon.client.season;

import java.text.NumberFormat;
import java.util.Locale;

import dev.fullmoon.client.network.SeasonProtocol;

/**
 * The words the season screen puts around the server's own. Amounts, names and times arrive
 * finished; what is said here is only which of the server's states a cell or a row is in, in the
 * same phrases the pass's chest menu uses, so the two read as one pass.
 */
public final class SeasonCopy {
    private SeasonCopy() {}

    public static String tab(SeasonProtocol.Tab tab) {
        return switch (tab) {
            case TRACK -> "Rewards";
            case MISSIONS -> "Missions";
            case PLUS -> "Plus";
        };
    }

    public static String track(SeasonProtocol.Track track) {
        return track == SeasonProtocol.Track.FREE ? "Free" : "Plus";
    }

    /**
     * The header's context. {@code endsIn} arrives with the phase's own wording in it — the
     * server names the phase and the time together — so it is presented unchanged; the one
     * client-owned line is the closed state that outlives the server's countdown.
     */
    public static String context(SeasonProtocol.Season season) {
        if (season.phase() == SeasonProtocol.Phase.ENDED) {
            return "Season ended";
        }
        return season.endsIn();
    }

    /** What a reward is, for the line under the track: a title is said to be one, a cosmetic too. */
    public static String kind(SeasonProtocol.Reward reward) {
        return switch (reward.kind()) {
            case WON -> "Currency";
            case TITLE -> "Season title";
            case COSMETIC -> "Growing cosmetic";
        };
    }

    /** The short word a cell shows when the reward's own name is wider than the cell. */
    public static String shortKind(SeasonProtocol.Reward reward) {
        return switch (reward.kind()) {
            case WON -> reward.label();
            case TITLE -> "Title";
            case COSMETIC -> "Cosmetic";
        };
    }

    /**
     * Where a cell stands, said for the line under the track. {@code xpLeft} is how much more the
     * player needs to reach the cell's level; it is only said while the season still pays. Once
     * the claim window has closed, a locked cell is closed by the calendar, not by the level.
     */
    public static String status(
            SeasonProtocol.Reward reward, int level, long xpLeft, String point,
            SeasonProtocol.Phase phase) {
        boolean claimOpen = phase != SeasonProtocol.Phase.ENDED;
        return switch (reward.status()) {
            case CLAIMED -> "Claimed";
            case CLAIMABLE -> !claimOpen ? "Claim window closed" : "Ready to claim";
            case LOCKED -> !claimOpen ? "Claim window closed"
                : xpLeft > 0
                    ? "Unlocks at level " + level + " · " + number(xpLeft) + " " + point + " to go"
                    : "Unlocks at level " + level;
            case PLUS_LOCKED -> !claimOpen ? "Claim window closed" : "Plus unlocks this reward";
            case PENDING -> claimOpen ? "Daily limit reached; claimable when it resets" : "Claim window closed";
        };
    }

    /** {@code 12 / 32}, with the thousands grouped and the server's own unit after the goal. */
    public static String count(long progress, long goal, String unit) {
        return number(progress) + " / " + number(goal) + unit;
    }

    /** {@code +40 XP}, in the season point's current name. */
    public static String reward(int xp, String point) {
        return "+" + number(xp) + " " + point;
    }

    /** {@code 95 / 147 XP}: the way to the next level. */
    public static String toNext(long into, long step, String point) {
        return number(into) + " / " + number(step) + " " + point;
    }

    public static String number(long value) {
        return NumberFormat.getIntegerInstance(Locale.ROOT).format(value);
    }

    /** A season mission's stage: the one the player is working on, of how many there are. */
    public static String stage(int tier, int tiers) {
        return "Stage " + Math.min(tier + 1, tiers) + " / " + tiers;
    }

    /** What the missions page says about a mission that cannot be tracked, or empty when it can be or is done. */
    public static String untrackable(SeasonProtocol.Mission mission, boolean plus) {
        if (mission.done() || mission.trackable()) {
            return "";
        }
        if (mission.plusExtra() && !plus) {
            return "Available with Plus";
        }
        if (mission.discord()) {
            return "Runs in Discord · link your Minecraft account";
        }
        return "Progresses in the wild";
    }
}
