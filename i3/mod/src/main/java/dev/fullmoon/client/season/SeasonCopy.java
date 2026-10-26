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
            case TRACK -> "보상";
            case MISSIONS -> "미션";
            case PLUS -> "플러스";
        };
    }

    public static String track(SeasonProtocol.Track track) {
        return track == SeasonProtocol.Track.FREE ? "무료" : "플러스";
    }

    /** The header's context: the time to the season's next boundary, named for the phase it ends. */
    public static String context(SeasonProtocol.Season season) {
        if (season.endsIn().isEmpty()) {
            return "";
        }
        return switch (season.phase()) {
            case UPCOMING -> "시작까지 " + season.endsIn();
            case LIVE -> season.endsIn();
            case CLAIM -> "보상 기간 · " + season.endsIn();
        };
    }

    /** What a reward is, for the line under the track: a title is said to be one, a cosmetic too. */
    public static String kind(SeasonProtocol.Reward reward) {
        return switch (reward.kind()) {
            case WON -> "돈";
            case TITLE -> "시즌 칭호";
            case COSMETIC -> "성장형 치장";
        };
    }

    /** The short word a cell shows when the reward's own name is wider than the cell. */
    public static String shortKind(SeasonProtocol.Reward reward) {
        return switch (reward.kind()) {
            case WON -> reward.label();
            case TITLE -> "칭호";
            case COSMETIC -> "치장";
        };
    }

    /**
     * Where a cell stands, said for the line under the track. {@code xpLeft} is how much more the
     * player needs to reach the cell's level; it is only said for a cell that is locked by level.
     */
    public static String status(SeasonProtocol.Reward reward, int level, long xpLeft, String point) {
        return switch (reward.status()) {
            case CLAIMED -> "받았어요";
            case CLAIMABLE -> "받을 수 있어요";
            case LOCKED -> xpLeft > 0
                ? level + "레벨에 열려요 · " + number(xpLeft) + " " + point + " 더 모으면 돼요"
                : level + "레벨에 열려요";
            case PLUS_LOCKED -> "플러스를 사면 바로 받을 수 있어요";
            case PENDING -> "오늘 받을 수 있는 돈을 다 채웠어요. 한도가 풀리면 받을 수 있어요";
        };
    }

    /** {@code 12 / 32개}, with the thousands grouped. */
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
        return "단계 " + Math.min(tier + 1, tiers) + " / " + tiers;
    }

    /** What the missions page says about a mission that cannot be tracked, or empty when it can be or is done. */
    public static String untrackable(SeasonProtocol.Mission mission, boolean plus) {
        if (mission.done() || mission.trackable()) {
            return "";
        }
        if (mission.plusExtra() && !plus) {
            return "플러스를 사면 바로 열려요";
        }
        if (mission.discord()) {
            return "디스코드에서 진행돼요 · 마크 계정을 연동해야 쌓여요";
        }
        return "야생에서 진행돼요";
    }
}
