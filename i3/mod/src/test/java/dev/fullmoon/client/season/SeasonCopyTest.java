package dev.fullmoon.client.season;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.fullmoon.client.network.SeasonProtocol;

import org.junit.jupiter.api.Test;

final class SeasonCopyTest {
    @Test
    void theHeaderNamesThePhaseItCountsDownTo() {
        assertEquals("시작까지 3일", SeasonCopy.context(season(SeasonProtocol.Phase.UPCOMING, "3일")));
        assertEquals("17일 남음", SeasonCopy.context(season(SeasonProtocol.Phase.LIVE, "17일 남음")));
        assertEquals("보상 기간 · 2일 3시간 남음", SeasonCopy.context(season(SeasonProtocol.Phase.CLAIM, "2일 3시간 남음")));
        assertEquals("", SeasonCopy.context(season(SeasonProtocol.Phase.LIVE, "")));
    }

    @Test
    void aCellSaysWhichOfTheServersStatesItIsIn() {
        assertEquals("받았어요", SeasonCopy.status(reward(SeasonProtocol.Status.CLAIMED), 3, 0, "XP"));
        assertEquals("받을 수 있어요", SeasonCopy.status(reward(SeasonProtocol.Status.CLAIMABLE), 3, 0, "XP"));
        assertEquals("30레벨에 열려요 · 1,204 XP 더 모으면 돼요",
            SeasonCopy.status(reward(SeasonProtocol.Status.LOCKED), 30, 1_204, "XP"));
        assertEquals("30레벨에 열려요", SeasonCopy.status(reward(SeasonProtocol.Status.LOCKED), 30, 0, "XP"));
        assertEquals("플러스를 사면 바로 받을 수 있어요",
            SeasonCopy.status(reward(SeasonProtocol.Status.PLUS_LOCKED), 3, 0, "XP"));
        assertEquals("오늘 받을 수 있는 돈을 다 채웠어요. 한도가 풀리면 받을 수 있어요",
            SeasonCopy.status(reward(SeasonProtocol.Status.PENDING), 3, 0, "XP"));
    }

    @Test
    void figuresAreGroupedAndThePointIsTheServersWord() {
        assertEquals("1,530 / 6,000개", SeasonCopy.count(1_530, 6_000, "개"));
        assertEquals("+40 별빛", SeasonCopy.reward(40, "별빛"));
        assertEquals("95 / 147 XP", SeasonCopy.toNext(95, 147, "XP"));
        assertEquals("단계 1 / 3", SeasonCopy.stage(0, 3));
        assertEquals("단계 3 / 3", SeasonCopy.stage(3, 3));
    }

    @Test
    void rewardKindsAndTabsHaveTheirWords() {
        assertEquals("돈", SeasonCopy.kind(reward(SeasonProtocol.Status.CLAIMED)));
        assertEquals("5천원", SeasonCopy.shortKind(reward(SeasonProtocol.Status.CLAIMED)));
        SeasonProtocol.Reward skin = new SeasonProtocol.Reward(SeasonProtocol.Kind.COSMETIC, "달빛 곡괭이",
            "minecraft:iron_pickaxe", SeasonProtocol.Status.LOCKED, "", "moonlight_pickaxe",
            "fullmoon.skin.moonlight_pickaxe.1");
        assertEquals("성장형 치장", SeasonCopy.kind(skin));
        assertEquals("치장", SeasonCopy.shortKind(skin));
        assertEquals("미션", SeasonCopy.tab(SeasonProtocol.Tab.MISSIONS));
        assertEquals("플러스", SeasonCopy.track(SeasonProtocol.Track.PLUS));
    }

    @Test
    void anUntrackableMissionSaysWhereItMoves() {
        assertEquals("", SeasonCopy.untrackable(mission(false, false, true, false), false));
        assertEquals("", SeasonCopy.untrackable(mission(true, false, false, false), false));
        assertEquals("플러스를 사면 바로 열려요", SeasonCopy.untrackable(mission(false, true, false, false), false));
        assertEquals("디스코드에서 진행돼요 · 마크 계정을 연동해야 쌓여요",
            SeasonCopy.untrackable(mission(false, false, false, true), true));
        assertEquals("야생에서 진행돼요", SeasonCopy.untrackable(mission(false, true, false, false), true));
    }

    private static SeasonProtocol.Season season(SeasonProtocol.Phase phase, String endsIn) {
        return new SeasonProtocol.Season("s1", "시즌 1", phase, endsIn, "");
    }

    private static SeasonProtocol.Reward reward(SeasonProtocol.Status status) {
        return new SeasonProtocol.Reward(SeasonProtocol.Kind.WON, "5천원", "minecraft:gold_nugget", status, "", "", "");
    }

    private static SeasonProtocol.Mission mission(boolean done, boolean plusExtra, boolean trackable, boolean discord) {
        return new SeasonProtocol.Mission("m", "미션", "minecraft:coal", 1, 10, "개", 30, 0, 1, done, plusExtra,
            false, trackable, discord);
    }
}
