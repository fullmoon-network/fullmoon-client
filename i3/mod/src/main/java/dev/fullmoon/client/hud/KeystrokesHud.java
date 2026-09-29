package dev.fullmoon.client.hud;

import java.util.ArrayDeque;
import java.util.Deque;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;

import net.minecraft.client.Minecraft;

/**
 * The movement keys, both mouse buttons with their clicks a second, and jump, as small caps of
 * HUD glass. A held key fills with gold, so a glance tells which keys are down; nothing else
 * about it moves.
 */
public final class KeystrokesHud extends BaseHudElement {
    private static final int KEY = Tokens.Size.HUD_KEY;
    private static final int KEY_GAP = Tokens.Space.TIGHT;
    private static final int WIDTH = KEY * 3 + KEY_GAP * 2;
    private static final int MOUSE_W = (WIDTH - KEY_GAP) / 2;
    private static final int MOUSE_H = Tokens.Space.LOOSE;
    private static final int SPACE_H = 5;
    private static final int SPACE_INSET = KEY;
    private static final int HEIGHT = KEY * 2 + MOUSE_H + SPACE_H + KEY_GAP * 3;

    private static final Deque<Long> LMB_CLICKS = new ArrayDeque<>();
    private static final Deque<Long> RMB_CLICKS = new ArrayDeque<>();
    private static boolean lastLmbState = false;
    private static boolean lastRmbState = false;

    public KeystrokesHud() {
        super("keystrokes", "키스트로크", "플레이어", true, Anchor.BOTTOM_RIGHT, Tokens.Space.COZY, Tokens.Space.COZY);
    }

    public static void recordClick(boolean lmb, boolean rmb) {
        long now = System.currentTimeMillis();
        if (lmb && !lastLmbState) {
            LMB_CLICKS.addLast(now);
        }
        if (rmb && !lastRmbState) {
            RMB_CLICKS.addLast(now);
        }
        lastLmbState = lmb;
        lastRmbState = rmb;
    }

    private static int getCps(Deque<Long> clicks, long now) {
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) {
            clicks.pollFirst();
        }
        return clicks.size();
    }

    @Override
    public int measureWidth(Minecraft client) {
        return WIDTH;
    }

    @Override
    public int measureHeight(Minecraft client) {
        return HEIGHT;
    }

    @Override
    public void draw(Painter painter, Box bounds, Minecraft client, boolean isEditor) {
        long now = System.currentTimeMillis();
        boolean live = !isEditor && client.options != null;
        boolean wDown = live && client.options.keyUp.isDown();
        boolean aDown = live && client.options.keyLeft.isDown();
        boolean sDown = live && client.options.keyDown.isDown();
        boolean dDown = live && client.options.keyRight.isDown();
        boolean lmbDown = live && client.options.keyAttack.isDown();
        boolean rmbDown = live && client.options.keyUse.isDown();
        boolean spaceDown = live && client.options.keyJump.isDown();
        if (isEditor) {
            wDown = true;
            dDown = true;
            spaceDown = true;
        }

        if (!isEditor) {
            recordClick(lmbDown, rmbDown);
        }
        int lmbCps = isEditor ? 7 : getCps(LMB_CLICKS, now);
        int rmbCps = isEditor ? 0 : getCps(RMB_CLICKS, now);

        int x = bounds.x();
        int y = bounds.y();
        key(painter, x + KEY + KEY_GAP, y, "W", wDown);
        int row = y + KEY + KEY_GAP;
        key(painter, x, row, "A", aDown);
        key(painter, x + KEY + KEY_GAP, row, "S", sDown);
        key(painter, x + (KEY + KEY_GAP) * 2, row, "D", dDown);
        row += KEY + KEY_GAP;
        mouse(painter, x, row, "L", lmbCps, lmbDown);
        mouse(painter, x + MOUSE_W + KEY_GAP, row, "R", rmbCps, rmbDown);
        row += MOUSE_H + KEY_GAP;
        space(painter, x, row, spaceDown);
    }

    private static void cap(Painter painter, int x, int y, int w, int h, boolean down) {
        painter.fill(x, y, w, h, Tokens.Radius.NONE, down ? Tokens.Color.ACCENT : Tokens.Color.SURFACE_GLASS_HUD);
    }

    private static void key(Painter painter, int x, int y, String name, boolean down) {
        cap(painter, x, y, KEY, KEY, down);
        Typeset.drawCentered(painter, Tokens.Type.STRONG, name, x + KEY / 2,
            Typeset.centred(Tokens.Type.STRONG, y, KEY),
            down ? Tokens.Color.INK_ON_ACCENT : Tokens.Color.INK_PRIMARY);
    }

    /** {@code L 7}: the button's letter in tertiary ink, its clicks a second in primary. */
    private static void mouse(Painter painter, int x, int y, String name, int cps, boolean down) {
        cap(painter, x, y, MOUSE_W, MOUSE_H, down);
        String rate = Integer.toString(cps);
        int nameW = Typeset.width(Tokens.Type.MICRO, name);
        int rateW = Typeset.tabularWidth(Tokens.Type.MICRO, rate);
        int gap = Tokens.Space.TIGHT + 1;
        int left = x + (MOUSE_W - nameW - gap - rateW) / 2;
        int textY = Typeset.centred(Tokens.Type.MICRO, y, MOUSE_H);
        Typeset.draw(painter, Tokens.Type.MICRO, name, left, textY,
            down ? Tokens.Color.INK_ON_ACCENT : Tokens.Color.INK_SECONDARY);
        Typeset.tabular(painter, Tokens.Type.MICRO, rate, left + nameW + gap, textY,
            down ? Tokens.Color.INK_ON_ACCENT : Tokens.Color.INK_PRIMARY);
    }

    private static void space(Painter painter, int x, int y, boolean down) {
        cap(painter, x, y, WIDTH, SPACE_H, down);
        painter.hRule(x + SPACE_INSET, y + SPACE_H / 2, WIDTH - SPACE_INSET * 2,
            down ? Tokens.Color.INK_ON_ACCENT : Tokens.Color.LINE_STRONG);
    }
}
