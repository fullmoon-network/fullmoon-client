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

    public CoordinatesHud() {
        super("coords", "좌표 및 방향", "플레이어", true, Anchor.TOP_LEFT, Tokens.Space.COZY, Tokens.Space.COZY);
    }

    @Override
    public int measureWidth(Minecraft client) {
        return chipWidth(0, parts(client, false));
    }

    @Override
    public int measureHeight(Minecraft client) {
        return CHIP_HEIGHT;
    }

    @Override
    public void draw(Painter painter, Box bounds, Minecraft client, boolean isEditor) {
        drawChip(painter, bounds, 0, parts(client, isEditor));
    }

    private static List<Part> parts(Minecraft client, boolean isEditor) {
        if (isEditor || client.player == null) {
            return List.of(new Part(SAMPLE_POSITION, SAMPLE_FACING));
        }
        Entity player = client.getCameraEntity() != null ? client.getCameraEntity() : client.player;
        String position = position(player.getX(), player.getY(), player.getZ());
        return List.of(new Part(position, facing(player.getDirection(), player.getYRot())));
    }

    /** Whole blocks, floored the way the F3 screen floors them. */
    static String position(double x, double y, double z) {
        return (int) Math.floor(x) + " · " + (int) Math.floor(y) + " · " + (int) Math.floor(z);
    }

    /** The cardinal letter and the heading in whole degrees, 0 to 359. */
    static String facing(Direction direction, float yaw) {
        int deg = Math.floorMod(Math.round(yaw), 360);
        String letter = switch (direction) {
            case NORTH -> "N";
            case SOUTH -> "S";
            case WEST -> "W";
            case EAST -> "E";
            default -> "?";
        };
        return letter + " " + deg + "°";
    }
}
