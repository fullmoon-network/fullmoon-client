package dev.fullmoon.client.hud;

import java.util.List;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;

/** Where the player stands, to the block, and which way they face: {@code 0 · 64 · 0  S 0°}. */
public final class CoordinatesHud extends BaseHudElement {
    private static final String SAMPLE_POSITION = "124 · 64 · -320";
    private static final String SAMPLE_FACING = "N 180°";
    private static final List<Part> SAMPLE = List.of(new Part(SAMPLE_POSITION, SAMPLE_FACING));

    /** The chip for the last position read, so a player standing still builds no strings. */
    private List<Part> live = List.of();
    private int liveX = Integer.MIN_VALUE;
    private int liveY;
    private int liveZ;
    private Direction liveDirection;
    private int liveDegrees;

    public CoordinatesHud() {
        super("coords", "좌표 및 방향", "플레이어", true, Anchor.TOP_LEFT, Tokens.Space.COZY, Tokens.Space.COZY);
    }

    @Override
    public int measureWidth(Minecraft client) {
        return measureWidth(client, false);
    }

    @Override
    public int measureWidth(Minecraft client, boolean isEditor) {
        return chipWidth(0, parts(client, isEditor));
    }

    @Override
    public int measureHeight(Minecraft client) {
        return CHIP_HEIGHT;
    }

    @Override
    public void draw(Painter painter, Box bounds, Minecraft client, boolean isEditor) {
        drawChip(painter, bounds, 0, parts(client, isEditor));
    }

    private List<Part> parts(Minecraft client, boolean isEditor) {
        if (isEditor || client.player == null) {
            return SAMPLE;
        }
        Entity player = client.getCameraEntity() != null ? client.getCameraEntity() : client.player;
        int x = (int) Math.floor(player.getX());
        int y = (int) Math.floor(player.getY());
        int z = (int) Math.floor(player.getZ());
        Direction direction = player.getDirection();
        int degrees = degrees(player.getYRot());
        if (x != liveX || y != liveY || z != liveZ || direction != liveDirection || degrees != liveDegrees) {
            liveX = x;
            liveY = y;
            liveZ = z;
            liveDirection = direction;
            liveDegrees = degrees;
            live = List.of(new Part(position(x, y, z), letter(direction) + " " + degrees + "°"));
        }
        return live;
    }

    /** Whole blocks, floored the way the F3 screen floors them. */
    static String position(double x, double y, double z) {
        return position((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    private static String position(int x, int y, int z) {
        return x + " · " + y + " · " + z;
    }

    /** The cardinal letter and the heading in whole degrees, 0 to 359. */
    static String facing(Direction direction, float yaw) {
        return letter(direction) + " " + degrees(yaw) + "°";
    }

    private static int degrees(float yaw) {
        return Math.floorMod(Math.round(yaw), 360);
    }

    private static String letter(Direction direction) {
        return switch (direction) {
            case NORTH -> "N";
            case SOUTH -> "S";
            case WEST -> "W";
            case EAST -> "E";
            default -> "?";
        };
    }
}
