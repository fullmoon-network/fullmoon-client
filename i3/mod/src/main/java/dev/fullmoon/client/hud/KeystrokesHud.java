package dev.fullmoon.client.hud;

import java.util.ArrayDeque;
import java.util.Deque;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.render.Rgb;
import dev.fullmoon.client.text.Typeset;

import net.minecraft.client.Minecraft;

/**
 * The movement keys, both mouse buttons with their clicks per second, and jump, as keycaps in the
 * palace frame: night glass in a faint gilt outline standing on a gilt lip. A held key sinks onto
 * its lip and fills with the accent, so a glance tells which keys are down.
 */
public final class KeystrokesHud extends BaseHudElement {
    private static final int KEY = 24;
    private static final int GAP = 2;
    private static final int WIDTH = KEY * 3 + GAP * 2;
    private static final int MOUSE_W = (WIDTH - GAP) / 2;
    private static final int MOUSE_H = 26;
    private static final int SPACE_H = 12;
    private static final int HEIGHT = KEY * 2 + MOUSE_H + SPACE_H + GAP * 3;
    private static final int LIP = 1;
    private static final int SPACE_INSET = 12;

    private static final Deque<Long> LMB_CLICKS = new ArrayDeque<>();
    private static final Deque<Long> RMB_CLICKS = new ArrayDeque<>();
    private static boolean lastLmbState = false;
    private static boolean lastRmbState = false;

    public KeystrokesHud() {
        super("keystrokes", "키스트로크", "플레이어", true, Anchor.BOTTOM_RIGHT, 16, 56);
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
        boolean wDown = !isEditor && client.options != null && client.options.keyUp.isDown();
        boolean aDown = !isEditor && client.options != null && client.options.keyLeft.isDown();
        boolean sDown = !isEditor && client.options != null && client.options.keyDown.isDown();
        boolean dDown = !isEditor && client.options != null && client.options.keyRight.isDown();
        boolean lmbDown = !isEditor && client.options != null && client.options.keyAttack.isDown();
        boolean rmbDown = !isEditor && client.options != null && client.options.keyUse.isDown();
        boolean spaceDown = !isEditor && client.options != null && client.options.keyJump.isDown();

        if (!isEditor) {
            recordClick(lmbDown, rmbDown);
        }

        int lmbCps = isEditor ? 10 : getCps(LMB_CLICKS, now);
        int rmbCps = isEditor ? 0 : getCps(RMB_CLICKS, now);

        int x = bounds.x();
        int y = bounds.y();
        key(painter, x + KEY + GAP, y, "W", wDown);
        int row = y + KEY + GAP;
        key(painter, x, row, "A", aDown);
        key(painter, x + KEY + GAP, row, "S", sDown);
        key(painter, x + (KEY + GAP) * 2, row, "D", dDown);
        row += KEY + GAP;
        mouse(painter, x, row, "LMB", lmbCps, lmbDown);
        mouse(painter, x + MOUSE_W + GAP, row, "RMB", rmbCps, rmbDown);
        row += MOUSE_H + GAP;
        space(painter, x, row, spaceDown);
    }

    /** A keycap's body. Returns the top of its face, which is one lip lower while it is held. */
    private static int cap(Painter painter, int x, int y, int w, int h, boolean down) {
        int top = down ? y + LIP : y;
        int face = h - LIP;
        painter.fill(x, top, w, face, Tokens.Radius.NONE,
            down ? Tokens.Color.ACCENT : Rgb.alpha(Tokens.Color.SURFACE_VOID, 0.82f));
        painter.border(x, top, w, face, Tokens.Radius.NONE, Tokens.Stroke.HAIR,
            down ? Tokens.Color.ACCENT_PRESSED : Tokens.Color.LINE_STRONG);
        if (!down) {
            painter.hRule(x, y + face, w, Tokens.Color.LINE_STRONG);
        }
        return top;
    }

    private static void key(Painter painter, int x, int y, String name, boolean down) {
        int top = cap(painter, x, y, KEY, KEY, down);
        Typeset.drawCentered(painter, Tokens.Type.STRONG, name, x + KEY / 2,
            Typeset.centred(Tokens.Type.STRONG, top, KEY - LIP),
            down ? Tokens.Color.INK_ON_ACCENT : Tokens.Color.INK_PRIMARY);
    }

    private static void mouse(Painter painter, int x, int y, String name, int cps, boolean down) {
        int top = cap(painter, x, y, MOUSE_W, MOUSE_H, down);
        int leading = Tokens.Type.MICRO.leading();
        int first = top + (MOUSE_H - LIP - leading * 2) / 2;
        Typeset.drawCentered(painter, Tokens.Type.MICRO, name, x + MOUSE_W / 2, first,
            down ? Tokens.Color.INK_ON_ACCENT : Tokens.Color.INK_TERTIARY);
        String rate = cps + " CPS";
        Typeset.tabular(painter, Tokens.Type.MICRO, rate,
            x + (MOUSE_W - Typeset.tabularWidth(Tokens.Type.MICRO, rate)) / 2, first + leading,
            down ? Tokens.Color.INK_ON_ACCENT : Tokens.Color.INK_PRIMARY);
    }

    private static void space(Painter painter, int x, int y, boolean down) {
        int top = cap(painter, x, y, WIDTH, SPACE_H, down);
        painter.fill(x + SPACE_INSET, top + (SPACE_H - LIP) / 2 - 1, WIDTH - SPACE_INSET * 2,
            Tokens.Stroke.FOCUS, Tokens.Radius.NONE,
            down ? Tokens.Color.INK_ON_ACCENT : Tokens.Color.LINE_STRONG);
    }
}
