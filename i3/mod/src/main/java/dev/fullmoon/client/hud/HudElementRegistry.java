package dev.fullmoon.client.hud;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Global registry and lifecycle manager for all in-game HUD modules. */
public final class HudElementRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("Fullmoon/Hud");
    private static final HudElementRegistry INSTANCE = new HudElementRegistry();

    private final Map<String, HudElement> registry = new LinkedHashMap<>();
    /** What {@link #elements} hands out every frame, rebuilt only when an element is registered. */
    private List<HudElement> snapshot = List.of();
    private final HudWatch watch = new HudWatch();
    private Path configPath;
    private int gridSnap = HudGrid.DEFAULT_STEP;

    private HudElementRegistry() {
        register(new CoordinatesHud());
        register(new FpsHud());
        register(new PingHud());
        register(new ClockHud());
        register(new KeystrokesHud());
        register(new ServerTickHud());
        register(new ArmorHud());
        register(new StatusEffectsHud());

        try {
            configPath = FabricLoader.getInstance().getConfigDir().resolve("fullmoon").resolve("hud.json");
            load();
        } catch (Exception ignored) {
        }
    }

    public static HudElementRegistry getInstance() {
        return INSTANCE;
    }

    public void register(HudElement element) {
        Objects.requireNonNull(element, "element");
        registry.put(element.id(), element);
        snapshot = Collections.unmodifiableList(new ArrayList<>(registry.values()));
    }

    public List<HudElement> elements() {
        return snapshot;
    }

    public HudElement get(String id) {
        return registry.get(id);
    }

    /** The snap step the file carries, which the launcher's editor may have chosen. */
    public int gridSnap() {
        return gridSnap;
    }

    public void load() {
        if (configPath == null) return;
        HudConfig config = HudConfig.load(configPath);
        applyConfig(config);
        watch.authored(modifiedMs());
    }

    public void save() {
        if (configPath == null) return;
        HudConfig config = exportConfig();
        config.save(configPath);
        watch.authored(modifiedMs());
    }

    /**
     * Takes an edit made to the file while the game is running — the launcher's HUD editor writes
     * the same {@code config/fullmoon/hud.json} this registry saves, so a layout chosen out there
     * has to land here without a restart.
     */
    public void poll(long nowMs) {
        if (configPath == null || !watch.due(nowMs)) return;
        long modified = modifiedMs();
        if (!watch.changed(modified)) return;
        HudConfig config = HudConfig.load(configPath);
        applyConfig(config);
        LOGGER.info("Adopted hud.json edited outside the game: {} element(s), mtime {}",
            config.elements == null ? 0 : config.elements.size(), modified);
    }

    private long modifiedMs() {
        try {
            return Files.getLastModifiedTime(configPath).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    public void applyConfig(HudConfig config) {
        if (config == null) return;
        gridSnap = HudGrid.sanitize(config.gridSnap);
        if (config.elements == null) return;
        for (Map.Entry<String, HudConfig.ElementState> entry : config.elements.entrySet()) {
            HudElement elem = registry.get(entry.getKey());
            if (elem != null && entry.getValue() != null) {
                HudConfig.ElementState state = entry.getValue();
                elem.setEnabled(state.enabled);
                try {
                    elem.setAnchor(Anchor.valueOf(state.anchor));
                } catch (Exception e) {
                    elem.setAnchor(Anchor.TOP_LEFT);
                }
                elem.setOffsetX(state.offsetX);
                elem.setOffsetY(state.offsetY);
                elem.setScale(state.scale > 0 ? state.scale : 1.0f);
            }
        }
    }

    public HudConfig exportConfig() {
        HudConfig config = new HudConfig();
        config.gridSnap = gridSnap;
        for (HudElement elem : registry.values()) {
            HudConfig.ElementState state = new HudConfig.ElementState(
                elem.enabled(),
                elem.anchor().name(),
                elem.offsetX(),
                elem.offsetY(),
                elem.scale()
            );
            config.elements.put(elem.id(), state);
        }
        return config;
    }
}
