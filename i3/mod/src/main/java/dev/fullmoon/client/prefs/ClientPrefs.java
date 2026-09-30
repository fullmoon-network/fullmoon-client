package dev.fullmoon.client.prefs;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.fullmoon.client.render.Motion;
import dev.fullmoon.client.sound.UiSounds;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.OptionInstance;

/**
 * The client's own two-line settings file: whether motion is reduced and whether the UI makes a
 * sound. Each is also an {@link OptionInstance}, so the settings screen lists them beside the
 * game's own options and toggles them the same way.
 */
public final class ClientPrefs {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "fullmoon-client.json";

    /** What the file holds. Public fields, because Gson fills it. */
    public static final class Saved {
        public boolean reduceMotion;
        public boolean uiSounds = true;
    }

    private static Saved saved = new Saved();
    private static Path path;

    public static final OptionInstance<Boolean> REDUCE_MOTION = OptionInstance.createBoolean(
        "fullmoon.settings.option.reduce_motion.label", false, value -> {
            saved.reduceMotion = value;
            Motion.reduce(value);
            save();
        });

    public static final OptionInstance<Boolean> UI_SOUNDS = OptionInstance.createBoolean(
        "fullmoon.settings.option.ui_sounds.label", true, value -> {
            saved.uiSounds = value;
            UiSounds.enabled(value);
            save();
        });

    private ClientPrefs() {}

    /** Reads the file once and pushes its values to the places that act on them. */
    public static void load() {
        path = FabricLoader.getInstance().getConfigDir().resolve(FILE);
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                Saved read = GSON.fromJson(reader, Saved.class);
                if (read != null) {
                    saved = read;
                }
            } catch (Exception ignored) {
                saved = new Saved();
            }
        }
        REDUCE_MOTION.set(saved.reduceMotion);
        UI_SOUNDS.set(saved.uiSounds);
        Motion.reduce(saved.reduceMotion);
        UiSounds.enabled(saved.uiSounds);
    }

    private static void save() {
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(saved, writer);
            }
        } catch (Exception ignored) {
        }
    }
}
