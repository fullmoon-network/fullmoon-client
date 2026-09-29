package dev.fullmoon.client.sound;

import java.util.EnumMap;
import java.util.Map;

import dev.fullmoon.client.FullmoonClient;
import dev.fullmoon.client.design.Tokens;

import net.fabricmc.api.ModInitializer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * The game half of {@link UiSounds}: the registered events, and the one call that plays a note
 * through the game's sound manager as a UI sound, so the master slider applies to it.
 *
 * <p>Registration is the {@code main} entrypoint, not the client one: the registries freeze
 * between the two, and a sound event registered from the client entrypoint throws.
 */
public final class GameSounds implements ModInitializer {
    private static final Map<UiSounds.Cue, SoundEvent> EVENTS = new EnumMap<>(UiSounds.Cue.class);

    @Override
    public void onInitialize() {
        if (!EVENTS.isEmpty()) {
            return;
        }
        for (UiSounds.Cue cue : UiSounds.Cue.values()) {
            Identifier id = Identifier.fromNamespaceAndPath(FullmoonClient.NAMESPACE, cue.path());
            SoundEvent event = SoundEvent.createVariableRangeEvent(id);
            Registry.register(BuiltInRegistries.SOUND_EVENT, id, event);
            EVENTS.put(cue, event);
        }
    }

    /** Installs the sink that plays through the game. Called once the client exists. */
    public static void install() {
        UiSounds.sink(GameSounds::play);
    }

    private static void play(UiSounds.Note note) {
        SoundEvent event = EVENTS.get(note.cue());
        Minecraft client = Minecraft.getInstance();
        if (event == null || client == null) {
            return;
        }
        client.getSoundManager().play(SimpleSoundInstance.forUI(event, note.pitch(), Tokens.Sound.VOLUME));
    }
}
