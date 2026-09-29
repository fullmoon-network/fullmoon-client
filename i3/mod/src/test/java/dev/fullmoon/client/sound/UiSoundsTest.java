package dev.fullmoon.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import dev.fullmoon.client.design.Tokens;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The gate under the focus tick, and the plumbing that lets a surface test run without a sound engine. */
final class UiSoundsTest {
    @AfterEach
    void restore() {
        UiSounds.sink(note -> {});
        UiSounds.enabled(true);
    }

    @Test
    void aHeldArrowKeyDoesNotMachineGunTheTick() {
        UiSounds.Gate gate = new UiSounds.Gate();
        Random random = new Random(1);
        int played = 0;
        // A key repeat at 30 ms for a second: only every second tick may sound at a 45 ms gate.
        for (long t = 0; t < 1000; t += 30) {
            if (gate.admit(UiSounds.Cue.FOCUS, t, random) != null) {
                played++;
            }
        }
        assertTrue(played <= 1000 / Tokens.Sound.FOCUS_INTERVAL_MS + 1, "played " + played);
        assertTrue(played >= 1000 / (Tokens.Sound.FOCUS_INTERVAL_MS * 2), "but it still ticks, played " + played);
    }

    @Test
    void theGateReopensAfterItsInterval() {
        UiSounds.Gate gate = new UiSounds.Gate();
        Random random = new Random(2);
        assertNotNull(gate.admit(UiSounds.Cue.FOCUS, 1_000, random));
        assertNull(gate.admit(UiSounds.Cue.FOCUS, 1_000 + Tokens.Sound.FOCUS_INTERVAL_MS - 1, random));
        assertNotNull(gate.admit(UiSounds.Cue.FOCUS, 1_000 + Tokens.Sound.FOCUS_INTERVAL_MS, random));
    }

    @Test
    void onlyTheTickIsGated() {
        UiSounds.Gate gate = new UiSounds.Gate();
        Random random = new Random(3);
        for (UiSounds.Cue cue : UiSounds.Cue.values()) {
            if (cue.gated()) {
                continue;
            }
            assertNotNull(gate.admit(cue, 0, random), cue.name());
            assertNotNull(gate.admit(cue, 0, random), cue.name() + " twice in the same millisecond");
            assertEquals(1.0f, gate.admit(cue, 0, random).pitch(), 0f, cue.name() + " keeps its pitch");
        }
    }

    @Test
    void repeatedTicksVaryInPitchWithinTheJitter() {
        UiSounds.Gate gate = new UiSounds.Gate();
        Random random = new Random(4);
        List<Float> pitches = new ArrayList<>();
        for (long t = 0; t < 5_000; t += 100) {
            pitches.add(gate.admit(UiSounds.Cue.FOCUS, t, random).pitch());
        }
        float lo = 1.0f - Tokens.Sound.FOCUS_PITCH_JITTER;
        float hi = 1.0f + Tokens.Sound.FOCUS_PITCH_JITTER;
        for (float pitch : pitches) {
            assertTrue(pitch >= lo && pitch <= hi, "pitch " + pitch);
        }
        assertTrue(pitches.stream().distinct().count() > 1, "not one machine");
    }

    @Test
    void theSinkHearsWhatPlaysAndTheSettingSilencesIt() {
        List<UiSounds.Note> heard = new ArrayList<>();
        UiSounds.sink(heard::add);
        UiSounds.play(UiSounds.Cue.CONFIRM);
        assertEquals(1, heard.size());
        assertEquals(UiSounds.Cue.CONFIRM, heard.getFirst().cue());
        UiSounds.enabled(false);
        UiSounds.play(UiSounds.Cue.CONFIRM);
        assertEquals(1, heard.size(), "off means off");
    }
}
