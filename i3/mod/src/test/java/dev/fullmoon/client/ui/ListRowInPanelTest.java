package dev.fullmoon.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.fullmoon.client.design.Tokens;

import org.junit.jupiter.api.Test;

/** A row a panel owns leaves the chosen wash and bar to the panel's gliding one and only lifts. */
final class ListRowInPanelTest {
    private static ListRow owned(boolean chosen) {
        ListRow row = new ListRow("accent", "E8C56C", () -> {});
        row.owned(true);
        row.selected(chosen);
        return row;
    }

    @Test
    void aChosenOwnedRowDrawsNoBarOfItsOwn() {
        ListRow.Look look = owned(true).look(State.REST);
        assertEquals(0, look.tickWidth(), "the panel's bar is the only bar");
        assertEquals(0, look.ground(), "the panel's wash is the only wash");
        assertEquals(Tokens.Color.INK_PRIMARY, look.ink(), "the chosen name still reads as chosen");
    }

    @Test
    void anOwnedRowStillLiftsUnderThePointer() {
        assertEquals(Tokens.Color.SURFACE_RAISED, owned(true).look(State.HOVER).ground());
        assertEquals(Tokens.Color.SURFACE_RAISED, owned(false).look(State.HOVER).ground());
    }

    @Test
    void theKeyboardsHairlineStaysOnAnUnchosenOwnedRow() {
        assertEquals(Tokens.Stroke.HAIR, owned(false).look(State.FOCUS_VISIBLE).tickWidth());
        assertEquals(0, owned(true).look(State.FOCUS_VISIBLE).tickWidth());
    }

    @Test
    void aStandaloneRowKeepsItsOwnBar() {
        ListRow row = new ListRow("accent", "E8C56C", () -> {});
        row.selected(true);
        assertEquals(Tokens.Stroke.BAR, row.look(State.REST).tickWidth());
        assertNotEquals(0, row.look(State.REST).ground());
    }

    @Test
    void theRailStartsEveryTabWhereTheOneBeforeItEnds() {
        int[] lefts = TabRail.lefts(10, new int[] {40, 50, 30}, 4);
        assertEquals(10, lefts[0]);
        assertEquals(54, lefts[1]);
        assertEquals(108, lefts[2]);
        assertEquals(1, TabRail.pick(60, 10, new int[] {40, 50, 30}, 4));
    }
}
