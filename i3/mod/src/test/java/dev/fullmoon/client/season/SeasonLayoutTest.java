package dev.fullmoon.client.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.network.SeasonProtocol;

import org.junit.jupiter.api.Test;

/** The pass's geometry at the 640×360 design size, where every part has to fit with nothing to spare. */
final class SeasonLayoutTest {
    private static final Box DESIGN = new Box(0, 0, 640, 360);
    private static final SeasonLayout FRAME = SeasonLayout.fit(DESIGN);
    private static final TrackLayout TRACK = FRAME.track();

    @Test
    void thePaneFillsTheDesignSizeAndCentresInABiggerOne() {
        assertEquals(new Box(24, 22, 592, 316), FRAME.panel());
        assertEquals(new Box(24, 78, 592, 236), FRAME.body());
        assertEquals(346, FRAME.hintY(), "the hint bar ends on the viewport's last row");
        assertEquals(new Box(184, 112, 592, 316), SeasonLayout.fit(new Box(0, 0, 960, 540)).panel());
        assertEquals(new Box(428, 86, 176, 220), FRAME.side());
        assertEquals(420, FRAME.divider());
        assertThrows(IllegalArgumentException.class, () -> SeasonLayout.fit(new Box(0, 0, 0, 360)));
    }

    @Test
    void theTrackStacksInsideTheBody() {
        assertEquals(new Box(76, 124, 528, 154), TRACK.window());
        assertEquals(138, TRACK.free().y());
        assertEquals(TRACK.free().bottom() + 2, TRACK.rail().y());
        assertEquals(TRACK.rail().bottom() + 2, TRACK.plus().y());
        assertTrue(TRACK.scrollbarY() < TRACK.detail().y());
        assertEquals(FRAME.body().bottom(), TRACK.detail().bottom());
    }

    @Test
    void theTrackOpensWithTheCurrentLevelAThirdOfTheWayIn() {
        assertEquals(1202f, TRACK.opening(26, 50));
        assertEquals(0f, TRACK.opening(0, 50));
        assertEquals(TRACK.maxScroll(50), TRACK.opening(49, 50));
        assertEquals(2072, TRACK.maxScroll(50));
        assertEquals(0, TRACK.maxScroll(10), "ten columns fit without scrolling");
    }

    @Test
    void revealMovesTheTrackOnlyAsFarAsItMust() {
        assertEquals(1202f, TRACK.reveal(30, 1202, 50));
        assertEquals(1604f, TRACK.reveal(40, 1202, 50));
        assertEquals(520f, TRACK.reveal(10, 1202, 50));
    }

    @Test
    void onlyATilePicksACell() {
        int x = TRACK.tileX(3, 0) + TrackLayout.TILE_W / 2;
        assertEquals(Optional.of(new TrackLayout.Cell(3, SeasonProtocol.Track.FREE)),
            TRACK.hit(x, TRACK.free().y() + 10, 0, 50));
        assertEquals(Optional.of(new TrackLayout.Cell(3, SeasonProtocol.Track.PLUS)),
            TRACK.hit(x, TRACK.plus().y() + 10, 0, 50));
        assertEquals(Optional.of(new TrackLayout.Cell(4, SeasonProtocol.Track.FREE)),
            TRACK.hit(x, TRACK.free().y() + 10, TrackLayout.PITCH, 50));
        assertTrue(TRACK.hit(x, TRACK.rail().y() + 4, 0, 50).isEmpty(), "the rail");
        assertTrue(TRACK.hit(TRACK.window().x() + 3 * TrackLayout.PITCH + 1, TRACK.free().y() + 10, 0, 50).isEmpty(),
            "the gap between two tiles");
        assertTrue(TRACK.hit(x, TRACK.free().y() + 10, 0, 3).isEmpty(), "past the last column");
        assertTrue(TRACK.hit(TRACK.window().x() - 4, TRACK.free().y() + 10, 0, 50).isEmpty(), "the gutter");
    }

    @Test
    void theThumbShowsHowMuchOfTheTrackIsInView() {
        assertNull(TRACK.thumb(0, 10));
        assertEquals(76, TRACK.thumb(0, 50)[0]);
        assertEquals(107, TRACK.thumb(0, 50)[1]);
        assertEquals(76 + 528 - 107, TRACK.thumb(2072, 50)[0]);
    }

    @Test
    void missionSectionsFollowEachOtherInReadingOrder() {
        MissionLayout list = FRAME.missions(4, 3, 7);

        assertEquals(new Box(24, 78, 392, 236), list.list());
        assertEquals(14, list.rows());
        MissionLayout.Line firstWeekly = list.row(4);
        assertEquals(MissionLayout.Section.WEEKLY, firstWeekly.section());
        assertEquals(0, firstWeekly.index());
        assertEquals(262, firstWeekly.y());
        assertEquals(550, list.contentHeight());
        assertEquals(314, list.maxScroll());
        assertNull(list.row(14));
    }

    @Test
    void anEmptyListHasNoSectionForIt() {
        MissionLayout weeklyOnly = FRAME.missions(0, 2, 0);
        MissionLayout none = FRAME.missions(0, 0, 0);

        assertEquals(MissionLayout.Section.WEEKLY, weeklyOnly.lines().get(0).section());
        assertTrue(weeklyOnly.lines().get(0).caption());
        assertEquals(2, weeklyOnly.rows());
        assertEquals(0, none.contentHeight());
        assertEquals(0, none.maxScroll());
        assertEquals(0f, none.reveal(0, 5));
    }

    @Test
    void aMissionRowIsPickedThroughTheScroll() {
        MissionLayout list = FRAME.missions(4, 3, 7);

        assertEquals(0, list.hit(100, 110, 0));
        assertEquals(1, list.hit(100, 120, 20));
        assertEquals(-1, list.hit(100, 90, 0), "a caption");
        assertEquals(-1, list.hit(500, 110, 0), "the side column");
    }

    @Test
    void revealBringsAFirstRowsCaptionWithIt() {
        MissionLayout list = FRAME.missions(4, 3, 7);

        assertEquals(0f, list.reveal(4, 0));
        assertEquals(20f, list.reveal(5, 0));
        assertEquals(0f, list.reveal(0, 100));
        assertEquals(158f, list.reveal(4, 300), "the weekly caption comes into view above its first row");
    }
}
