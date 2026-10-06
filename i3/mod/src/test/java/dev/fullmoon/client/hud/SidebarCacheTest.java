package dev.fullmoon.client.hud;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** A frame the server said nothing in must reuse the layout; any change that shows must not. */
final class SidebarCacheTest {
    private static final Object OBJECTIVE = new Object();
    private static final NumberFormat NO_FORMAT = null;
    private static final ScoreboardSidebar.Layout LAYOUT = new ScoreboardSidebar.Layout(40, "풀문", 0, List.of());

    private final Scoreboard scoreboard = new Scoreboard();
    private final SidebarCache cache = new SidebarCache();
    private List<PlayerScoreEntry> scores;
    private Component title;

    @BeforeEach
    void settled() {
        title = Component.literal("풀문");
        scores = List.of(new PlayerScoreEntry("소지금", 3, null, null), new PlayerScoreEntry("접속자", 1, null, null));
        cache.store(OBJECTIVE, title, NO_FORMAT, scores, scoreboard, 2, 0, LAYOUT);
    }

    private boolean matches() {
        return cache.matches(OBJECTIVE, title, NO_FORMAT, scores, scoreboard, 2, 0);
    }

    @Test
    void anUnchangedScoreboardKeepsTheLayoutItHad() {
        assertTrue(matches());
        assertSame(LAYOUT, cache.layout());
        assertTrue(cache.matches(OBJECTIVE, Component.literal("풀문"), NO_FORMAT,
            List.of(new PlayerScoreEntry("소지금", 3, null, null), new PlayerScoreEntry("접속자", 1, null, null)),
            scoreboard, 2, 0), "equal contents in new objects are the same sidebar");
    }

    @Test
    void aNewCacheHasNothingToReuse() {
        assertFalse(new SidebarCache().matches(OBJECTIVE, title, NO_FORMAT, scores, scoreboard, 2, 0));
    }

    @Test
    void aChangedScoreIsRelaidOut() {
        scores = List.of(new PlayerScoreEntry("소지금", 4, null, null), new PlayerScoreEntry("접속자", 1, null, null));
        assertFalse(matches());
    }

    @Test
    void anAddedOrRemovedLineIsRelaidOut() {
        scores = List.of(new PlayerScoreEntry("소지금", 3, null, null));
        assertFalse(matches());
        scores = List.of(new PlayerScoreEntry("소지금", 3, null, null), new PlayerScoreEntry("접속자", 1, null, null),
            new PlayerScoreEntry("위치", 0, null, null));
        assertFalse(matches());
    }

    @Test
    void aChangedLineNameOrDisplayIsRelaidOut() {
        scores = List.of(new PlayerScoreEntry("소지금", 3, Component.literal("돈"), null),
            new PlayerScoreEntry("접속자", 1, null, null));
        assertFalse(matches());
    }

    @Test
    void aChangedTitleIsRelaidOut() {
        title = Component.literal("달빛");
        assertFalse(matches());
    }

    @Test
    void aNewObjectiveIsRelaidOutEvenWithTheSameLines() {
        assertFalse(cache.matches(new Object(), title, NO_FORMAT, scores, scoreboard, 2, 0));
    }

    @Test
    void aNewGuiScaleOrFontEpochIsRelaidOut() {
        assertFalse(cache.matches(OBJECTIVE, title, NO_FORMAT, scores, scoreboard, 3, 0));
        assertFalse(cache.matches(OBJECTIVE, title, NO_FORMAT, scores, scoreboard, 2, 1));
    }

    @Test
    void aTeamPrefixSuffixOrColourIsPartOfTheLine() {
        PlayerTeam team = scoreboard.addPlayerTeam("lobby");
        scoreboard.addPlayerToTeam("소지금", team);
        assertFalse(matches(), "joining a team changes the line's name");

        cache.store(OBJECTIVE, title, NO_FORMAT, scores, scoreboard, 2, 0, LAYOUT);
        assertTrue(matches());

        team.setPlayerPrefix(Component.literal("◆ "));
        assertFalse(matches());
        cache.store(OBJECTIVE, title, NO_FORMAT, scores, scoreboard, 2, 0, LAYOUT);
        assertTrue(matches());

        team.setPlayerSuffix(Component.literal("!"));
        assertFalse(matches());
        cache.store(OBJECTIVE, title, NO_FORMAT, scores, scoreboard, 2, 0, LAYOUT);
        assertTrue(matches());

        team.setColor(ChatFormatting.GOLD);
        assertFalse(matches());
        cache.store(OBJECTIVE, title, NO_FORMAT, scores, scoreboard, 2, 0, LAYOUT);
        assertTrue(matches());

        scoreboard.removePlayerFromTeam("소지금", team);
        assertFalse(matches());
    }
}
