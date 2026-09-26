package dev.fullmoon.client.title;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

final class MoonPhaseTest {
    private static final Instant EPOCH_NEW = Instant.parse("2000-01-06T18:14:00Z");

    private static Instant daysAfterEpoch(double days) {
        return EPOCH_NEW.plus(Duration.ofMillis(Math.round(days * 86_400_000.0)));
    }

    @Test
    void theEpochIsANewMoon() {
        MoonPhase phase = MoonPhase.at(EPOCH_NEW);
        assertEquals(0.0f, phase.lit(), 1e-6f);
        assertEquals(MoonPhase.Name.NEW, phase.name());
    }

    @Test
    void aQuarterMonthLaterHalfTheDiscIsLitOnTheRight() {
        MoonPhase phase = MoonPhase.at(daysAfterEpoch(MoonPhase.SYNODIC / 4.0));
        assertEquals(0.5f, phase.lit(), 1e-4f);
        assertTrue(phase.waxing());
        assertEquals(MoonPhase.Name.FIRST_QUARTER, phase.name());
    }

    @Test
    void threeQuartersLaterItIsWaning() {
        MoonPhase phase = MoonPhase.at(daysAfterEpoch(MoonPhase.SYNODIC * 0.75));
        assertEquals(0.5f, phase.lit(), 1e-4f);
        assertFalse(phase.waxing());
        assertEquals(MoonPhase.Name.LAST_QUARTER, phase.name());
    }

    /** The harvest moon of 2026: full on the evening of 26 September in Korea. */
    @Test
    void twentySixthOfSeptember2026IsFull() {
        MoonPhase phase = MoonPhase.at(Instant.parse("2026-09-26T13:00:00Z"));
        assertTrue(phase.full());
        assertEquals(MoonPhase.Name.FULL, phase.name());
        assertEquals(0, phase.daysToFull());
        assertEquals(30, phase.daysToNextFull());
    }

    @Test
    void aWeekBeforeFullTheCountdownIsAWeek() {
        MoonPhase phase = MoonPhase.at(Instant.parse("2026-09-19T13:00:00Z"));
        assertTrue(phase.waxing());
        assertEquals(7, phase.daysToFull());
    }

    @Test
    void datesBeforeTheEpochStillLandInsideTheMonth() {
        MoonPhase phase = MoonPhase.at(daysAfterEpoch(-MoonPhase.SYNODIC * 3 - 1));
        assertTrue(phase.age() >= 0 && phase.age() < MoonPhase.SYNODIC);
    }
}
