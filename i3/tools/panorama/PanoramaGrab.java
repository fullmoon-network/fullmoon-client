package dev.fullmoon.tools.panorama;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.server.IntegratedServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes the title-screen panorama from inside a singleplayer world, as run/panorama-steps.txt says.
 *
 * <p>Not part of the mod: tools/studio-panorama.sh copies this into a throwaway build tree in the
 * Studio and registers it there, so the shipped jar never carries it. The grab itself is the game's
 * own {@link Minecraft#grabPanoramixScreenshot}, which renders the six faces at 4096 px and writes
 * them at 1024 px in the order and orientation {@code CubeMapTexture} reads them back in.
 *
 * <p>A face is only grabbed once the world behind it has been built. The section compiler only
 * works on what the camera can see, so the camera is turned to all six faces first and held on
 * each until nothing is queued and the counts have stopped moving; a grab straight after a teleport
 * is six photographs of half-streamed chunks.
 */
public final class PanoramaGrab implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("Fullmoon/Panorama");
    private static final long STABLE_NANOS = 4_000_000_000L;
    private static final long DIRECTION_TIMEOUT_NANOS = 180_000_000_000L;
    private static final long WRITTEN_NANOS = 2_000_000_000L;
    private static final long WRITE_TIMEOUT_NANOS = 120_000_000_000L;
    private static final float[][] FACE_TURNS =
        {{0, 0}, {90, 0}, {180, 0}, {-90, 0}, {0, -90}, {0, 90}};
    private static final int FACES = 6;

    private final Deque<String[]> steps = new ArrayDeque<>();
    private boolean started;
    private boolean finished;

    private String[] step;
    private Deque<float[]> directions;
    private long directionSince;
    private long stableSince;
    private int lastSections = -1;
    private int lastChunks = -1;
    private List<Path> awaited;
    private String awaitedFaces;
    private long awaitedSince;
    private long writtenBytes;
    private long writtenSince;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (finished || client.level == null || client.player == null) {
            return;
        }
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        if (client.screen instanceof PauseScreen) {
            client.setScreen(null);
        }
        if (client.screen != null) {
            return;
        }
        if (!started) {
            started = true;
            load(client);
            client.options.hideGui = true;
            LOG.info("panorama: world up, {} steps", steps.size());
        }
        if (awaited != null) {
            awaitWrites(client);
            return;
        }
        if (step == null) {
            next(client, server);
            return;
        }
        if (!settle(client)) {
            return;
        }
        if (!directions.isEmpty()) {
            face(client, directions.poll());
            return;
        }
        shoot(client);
        step = null;
    }

    private void load(Minecraft client) {
        Path script = client.gameDirectory.toPath().resolve("panorama-steps.txt");
        try {
            for (String line : Files.readAllLines(script)) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    steps.add(trimmed.split("\\s+"));
                }
            }
        } catch (IOException e) {
            LOG.error("panorama: cannot read {}", script, e);
        }
    }

    private void next(Minecraft client, IntegratedServer server) {
        String[] line = steps.poll();
        if (line == null) {
            finished = true;
            LOG.info("panorama: done");
            client.stop();
            return;
        }
        LOG.info("panorama: step {}", String.join(" ", line));
        switch (line[0]) {
            case "command" -> {
                String command = String.join(" ", Arrays.copyOfRange(line, 1, line.length));
                server.execute(() -> server.getCommands()
                    .performPrefixedCommand(server.createCommandSourceStack(), command));
            }
            case "gamma" -> client.options.gamma().set(Double.parseDouble(line[1]));
            case "view", "panorama" -> {
                boolean view = line[0].equals("view");
                float yaw = Float.parseFloat(line[5]);
                float pitch = view ? Float.parseFloat(line[6]) : 0.0f;
                String command = "tp @a %s %s %s %s 0".formatted(line[2], line[3], line[4], yaw);
                server.execute(() -> server.getCommands()
                    .performPrefixedCommand(server.createCommandSourceStack(), command));
                step = line;
                directions = new ArrayDeque<>();
                for (float[] turn : FACE_TURNS) {
                    directions.add(new float[] {yaw + turn[0], turn[1]});
                }
                directions.add(new float[] {yaw, pitch});
                face(client, directions.poll());
            }
            default -> LOG.error("panorama: unknown step {}", line[0]);
        }
    }

    private void face(Minecraft client, float[] rotation) {
        client.player.setYRot(rotation[0]);
        client.player.setXRot(rotation[1]);
        client.player.yRotO = rotation[0];
        client.player.xRotO = rotation[1];
        directionSince = System.nanoTime();
        stableSince = directionSince;
        lastSections = -1;
        lastChunks = -1;
    }

    private boolean settle(Minecraft client) {
        long now = System.nanoTime();
        int sections = client.levelRenderer.countRenderedSections();
        int chunks = client.level.getChunkSource().getLoadedChunksCount();
        boolean idle = client.levelRenderer.hasRenderedAllSections();
        if (!idle || sections != lastSections || chunks != lastChunks) {
            stableSince = now;
            lastSections = sections;
            lastChunks = chunks;
        }
        if (now - stableSince >= STABLE_NANOS) {
            LOG.info("panorama: settled yaw {} pitch {} after {} ms, {} sections, {} chunks",
                client.player.getYRot(), client.player.getXRot(),
                (now - directionSince) / 1_000_000, sections, chunks);
            return true;
        }
        if (now - directionSince >= DIRECTION_TIMEOUT_NANOS) {
            LOG.warn("panorama: SOFT yaw {} pitch {}: never settled, {}", client.player.getYRot(),
                client.player.getXRot(), client.levelRenderer.getSectionStatistics());
            return true;
        }
        return false;
    }

    private void shoot(Minecraft client) {
        String name = step[1];
        Path shots = client.gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR);
        if (step[0].equals("view")) {
            String file = "view-" + name + ".png";
            Screenshot.grab(client.gameDirectory, file, client.getMainRenderTarget(), 1,
                message -> LOG.info("panorama: {}", message.getString()));
            awaited = List.of(shots.resolve(file));
        } else {
            Path[] faces = new Path[FACES];
            for (int i = 0; i < FACES; i++) {
                faces[i] = shots.resolve("panorama_" + i + ".png");
                try {
                    Files.deleteIfExists(faces[i]);
                } catch (IOException e) {
                    LOG.error("panorama: cannot clear {}", faces[i], e);
                }
            }
            LOG.info("panorama: grab {} -> {}", name,
                client.grabPanoramixScreenshot(client.gameDirectory).getString());
            awaited = List.of(faces);
            awaitedFaces = name;
        }
        awaitedSince = System.nanoTime();
        writtenSince = awaitedSince;
        writtenBytes = -1;
    }

    /** The PNGs are encoded off the render thread, so a file is done when its size stops moving. */
    private void awaitWrites(Minecraft client) {
        long now = System.nanoTime();
        long bytes = 0;
        for (Path path : awaited) {
            try {
                long size = Files.size(path);
                bytes = size > 0 && bytes >= 0 ? bytes + size : -1;
            } catch (IOException e) {
                bytes = -1;
            }
        }
        if (bytes != writtenBytes) {
            writtenBytes = bytes;
            writtenSince = now;
        }
        boolean written = bytes > 0 && now - writtenSince >= WRITTEN_NANOS;
        if (!written && now - awaitedSince < WRITE_TIMEOUT_NANOS) {
            return;
        }
        if (!written) {
            LOG.error("panorama: timed out waiting for {}", awaited);
        } else if (awaitedFaces != null) {
            Path target = awaited.get(0).resolveSibling(awaitedFaces);
            try {
                Files.createDirectories(target);
                for (Path face : awaited) {
                    Files.move(face, target.resolve(face.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
                }
                LOG.info("panorama: wrote {}", target);
            } catch (IOException e) {
                LOG.error("panorama: cannot move faces into {}", target, e);
            }
        } else {
            LOG.info("panorama: wrote {}", awaited.get(0));
        }
        awaited = null;
        awaitedFaces = null;
    }
}
