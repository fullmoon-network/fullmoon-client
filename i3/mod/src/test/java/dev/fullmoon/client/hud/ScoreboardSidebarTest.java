package dev.fullmoon.client.hud;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.fullmoon.client.layout.Box;

/** The live rehearsal (2026-09-27) saw the keystrokes cover the sidebar at 1280x720, gui scale 2. */
final class ScoreboardSidebarTest {
    /** 640x360 gui px, with the keystrokes where they sit by default: bottom right, 16 and 56 in. */
    private static final Box KEYSTROKES = new Box(640 - 16 - 76, 360 - 56 - 92, 76, 92);

    @Test
    void theSidebarLiftsClearOfTheKeystrokes() {
        Box at = ScoreboardSidebar.place(640, 360, 120, 95, List.of(KEYSTROKES));
        assertTrue(at.bottom() <= KEYSTROKES.y(), at + " still reaches into " + KEYSTROKES);
        assertEquals(640 - 8, at.right(), "it keeps vanilla's right edge");
    }

    @Test
    void withNothingInTheWayItSitsWhereVanillaPutsIt() {
        Box at = ScoreboardSidebar.place(640, 360, 120, 95, List.of());
        assertEquals(180 - 95 / 3, at.y());
    }

    @Test
    void aSidebarTooTallToLiftMovesLeftOfTheKeystrokes() {
        Box at = ScoreboardSidebar.place(640, 360, 120, 240, List.of(KEYSTROKES));
        assertTrue(at.right() <= KEYSTROKES.x(), at + " overlaps " + KEYSTROKES);
    }

    @Test
    void ruleLinesAreRecognisedAndWordsAreNot() {
        assertTrue(ScoreboardSidebar.isRule("────────"));
        assertTrue(ScoreboardSidebar.isRule(" ---- "));
        assertFalse(ScoreboardSidebar.isRule("소지금 미연동"));
        assertFalse(ScoreboardSidebar.isRule(" "));
    }
}
