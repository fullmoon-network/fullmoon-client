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
    void withNothingInTheWayItSitsWhereTheMockupPutsIt() {
        Box at = ScoreboardSidebar.place(640, 360, 132, 95, List.of());
        assertEquals(100, at.y());
        assertEquals(640 - 8 - 132, at.x());
    }

    @Test
    void aShortScreenKeepsTheSidebarOnIt() {
        Box at = ScoreboardSidebar.place(640, 120, 132, 95, List.of());
        assertEquals(120 - 95 - 8, at.y());
    }

    @Test
    void helpLinesAreCommands() {
        assertTrue(ScoreboardSidebar.isHelp("/텔레포트 로비 곳곳으로 이동"));
        assertTrue(ScoreboardSidebar.isHelp("§f/텔레포트 §7로비 곳곳으로 이동"));
        assertFalse(ScoreboardSidebar.isHelp("소지금 2억원"));
        assertTrue(ScoreboardSidebar.isHelp("§7도움말 §f/텔레포트 · 로비 곳곳으로 이동"), "the lobby labels its help line");
        assertEquals("/텔레포트 로비 곳곳으로 이동", ScoreboardSidebar.helpText("§7도움말 §f/텔레포트 · 로비 곳곳으로 이동"));
        assertEquals("/텔레포트 로비 곳곳으로 이동", ScoreboardSidebar.helpText("/텔레포트 로비 곳곳으로 이동"));
    }

    @Test
    void aLineSplitsIntoLabelAndValueAtItsLastSpace() {
        ScoreboardSidebar.Split split = ScoreboardSidebar.split(List.of(
            new ScoreboardSidebar.Run("소지금 ", 0), new ScoreboardSidebar.Run("2억원", 0xFF123456)));
        assertEquals("소지금", split.label());
        assertEquals("2억원", split.value());
        assertEquals(0xFF123456, split.valueColor());

        ScoreboardSidebar.Split whole = ScoreboardSidebar.split(List.of(new ScoreboardSidebar.Run("오늘도 즐겁게", 0)));
        assertEquals("오늘도 즐겁게", whole.label(), "a sentence in one colour is all label");
        assertEquals("", whole.value());

        ScoreboardSidebar.Split figure = ScoreboardSidebar.split(List.of(new ScoreboardSidebar.Run("접속자 1명", 0)));
        assertEquals("접속자", figure.label(), "a figure is a value even in the label's colour");
        assertEquals("1명", figure.value());

        ScoreboardSidebar.Split coloured = ScoreboardSidebar.split(List.of(
            new ScoreboardSidebar.Run("위치 ", 0xFFAAAAAA), new ScoreboardSidebar.Run("로비", 0xFF7FD8E8)));
        assertEquals("위치", coloured.label());
        assertEquals("로비", coloured.value());
        assertEquals(0xFF7FD8E8, coloured.valueColor());

        ScoreboardSidebar.Split trailing = ScoreboardSidebar.split(List.of(new ScoreboardSidebar.Run("위치 ", 0)));
        assertEquals("위치", trailing.label());
        assertEquals("", trailing.value());
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
        assertTrue(ScoreboardSidebar.isRule("────────§0"));
        assertTrue(ScoreboardSidebar.isRule("§8────────§r"));
        assertFalse(ScoreboardSidebar.isRule("소지금 미연동"));
        assertFalse(ScoreboardSidebar.isRule(" "));
        assertFalse(ScoreboardSidebar.isRule("§0"));
        assertFalse(ScoreboardSidebar.isRule("도움말§1"));
    }
}
