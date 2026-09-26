package dev.fullmoon.client.title;

import java.time.Duration;
import java.time.Instant;

/**
 * Tonight's moon, from the date alone.
 *
 * <p>A mean synodic month counted from one observed new moon. The real moon runs up to about half
 * a day either side of the mean, which is below what a phase name or a whole-day countdown can
 * show, so no ephemeris is worth carrying for it.
 *
 * @param age    days since the last new moon, {@code [0, SYNODIC)}
 * @param lit    fraction of the disc that is lit, 0 new to 1 full
 * @param waxing true from new moon to full, when the lit limb is on the right
 */
public record MoonPhase(double age, float lit, boolean waxing) {
    public static final double SYNODIC = 29.530588853;
    /** The new moon of 2000-01-06 18:14 UTC. */
    private static final Instant EPOCH = Instant.parse("2000-01-06T18:14:00Z");
    /** Lit this much or more, the disc reads as full to the eye. */
    private static final float FULL = 0.98f;
    private static final float NEW = 0.02f;

    public enum Name { NEW, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS, FULL, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT }

    public static MoonPhase at(Instant instant) {
        double days = Duration.between(EPOCH, instant).toMillis() / 86_400_000.0;
        double age = ((days % SYNODIC) + SYNODIC) % SYNODIC;
        float lit = (float) ((1.0 - Math.cos(2.0 * Math.PI * age / SYNODIC)) / 2.0);
        return new MoonPhase(age, lit, age < SYNODIC / 2.0);
    }

    public boolean full() {
        return lit >= FULL;
    }

    /** Eight names, each centred on its point of the month. */
    public Name name() {
        if (lit >= FULL) {
            return Name.FULL;
        }
        if (lit <= NEW) {
            return Name.NEW;
        }
        int octant = (int) Math.floor(age / SYNODIC * 8.0 + 0.5) % 8;
        return switch (octant) {
            case 1 -> Name.WAXING_CRESCENT;
            case 2 -> Name.FIRST_QUARTER;
            case 3 -> Name.WAXING_GIBBOUS;
            case 5 -> Name.WANING_GIBBOUS;
            case 6 -> Name.LAST_QUARTER;
            case 7 -> Name.WANING_CRESCENT;
            default -> waxing ? Name.WAXING_GIBBOUS : Name.WANING_CRESCENT;
        };
    }

    /** Days until the next full moon, to the nearest day; 0 only on the night itself. */
    public int daysToFull() {
        if (full()) {
            return 0;
        }
        double toFull = (SYNODIC / 2.0 - age + SYNODIC) % SYNODIC;
        return Math.max(1, (int) Math.round(toFull));
    }

    /** On a full night, days to the full moon after this one, to the nearest day. */
    public int daysToNextFull() {
        return (int) Math.round(SYNODIC / 2.0 - age + SYNODIC);
    }
}
