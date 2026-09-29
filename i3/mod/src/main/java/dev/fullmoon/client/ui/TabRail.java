package dev.fullmoon.client.ui;

import java.util.List;
import java.util.function.IntConsumer;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.sound.UiSounds;
import dev.fullmoon.client.text.Typeset;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * The pages of a screen, as words on a rule, with the one you are on underlined.
 *
 * <p>One keyboard stop for the rail and arrows inside it, the same bargain {@link ListPanel} makes.
 * The arrows move a mark and do not switch pages: a tab here replaces the whole screen, and a
 * player walking the rail with the arrow keys would fire off three page loads to reach the fourth
 * tab. So the mark moves, Enter commits, and the two are told apart the way a list tells them
 * apart — the chosen tab is underlined in accent, the marked one in the same strong line a hover
 * draws.
 *
 * <p>An underline and not a pill. A pill per tab is five more boxes on a surface that already has
 * boxes in it, and the rule the tabs sit on is a line the layout wanted anyway.
 *
 * <p>Where the tabs fall is static integer arithmetic over their widths, so the hit test can be
 * checked without a font. Measuring the words is the only part that needs a game.
 */
public final class TabRail extends Widget {
    public static final int HEIGHT = Tokens.Space.SECTION;

    /** Between two tabs, and inside one on each side of its name. */
    private static final int GAP = Tokens.Space.SNUG;
    private static final int PAD = Tokens.Space.LOOSE;

    private final List<String> tabs;
    private final IntConsumer onPick;
    private int index;
    private int marked;

    /** The tab the pointer is on, or -1. Read every frame from {@link #hovering}. */
    private int over = -1;

    /** The tab the pointer went down on, so a press that slides onto another tab picks neither. */
    private int pressed = -1;

    public TabRail(String label, List<String> tabs, int index, IntConsumer onPick) {
        super(Voice.QUIET, label);
        this.tabs = List.copyOf(tabs);
        this.index = Math.clamp(index, 0, this.tabs.size() - 1);
        this.marked = this.index;
        this.onPick = onPick;
    }

    public int index() {
        return index;
    }

    /** The tab the keyboard is on. Equal to {@link #index} until an arrow moves it. */
    public int marked() {
        return marked;
    }

    public String picked() {
        return tabs.get(index);
    }

    @Override
    public void draw(Painter painter, State state) {
        Box b = bounds();
        Chrome chrome = voice().chrome(state);
        int base = b.bottom() - Tokens.Stroke.HAIR;
        painter.hRule(b.x(), base, b.w(), chrome.line());

        int[] widths = widths();
        int y = Typeset.centred(Tokens.Type.MICRO, b.y(), b.h() - Tokens.Stroke.FOCUS);
        int x = b.x();
        for (int i = 0; i < tabs.size(); i++) {
            boolean here = i == index;
            boolean touched = state.live() && (i == over || (state == State.FOCUS_VISIBLE && i == marked));
            Typeset.draw(painter, Tokens.Type.MICRO, tabs.get(i), x + PAD, y,
                ink(state, chrome, here || touched));
            if (here) {
                painter.fill(x, base - Tokens.Stroke.HAIR, widths[i], Tokens.Stroke.FOCUS,
                    state.live()
                        ? (state == State.ACTIVE ? Tokens.Color.ACCENT_PRESSED : Tokens.Color.ACCENT)
                        : chrome.ink());
            } else if (touched) {
                // The mark rehearses the underline it would leave behind, one hairline shy of it.
                painter.hRule(x, base, widths[i], Tokens.Color.LINE_STRONG);
            }
            x += widths[i] + GAP;
        }
        if (state == State.LOADING) {
            Dots.draw(painter, x + PAD + Dots.width() / 2.0f, b.midY(), chrome.ink());
        }
        ring(painter, state, Tokens.Radius.SM);
    }

    /** A name the player has reached is primary; the rest of the rail sits back at secondary. */
    private static int ink(State state, Chrome chrome, boolean lit) {
        if (!state.live()) {
            return chrome.ink();
        }
        return lit ? Tokens.Color.INK_PRIMARY : Tokens.Color.INK_SECONDARY;
    }

    @Override
    protected void hovering(double mx, double my) {
        over = pick(mx, bounds().x(), widths(), GAP);
    }

    @Override
    protected boolean press(double mx, double my) {
        pressed = pick(mx, bounds().x(), widths(), GAP);
        return pressed >= 0;
    }

    /** A press that comes up on the tab it went down on. Anywhere else it picked nothing. */
    @Override
    protected void release(double mx, double my, boolean inside) {
        int up = pick(mx, bounds().x(), widths(), GAP);
        if (inside && up >= 0 && up == pressed) {
            choose(up);
        }
        pressed = -1;
    }

    @Override
    protected boolean key(Chord chord) {
        if (chord.is(InputConstants.KEY_LEFT)) {
            marked = Math.max(0, marked - 1);
            return true;
        }
        if (chord.is(InputConstants.KEY_RIGHT)) {
            marked = Math.min(tabs.size() - 1, marked + 1);
            return true;
        }
        if (chord.is(InputConstants.KEY_HOME)) {
            marked = 0;
            return true;
        }
        if (chord.is(InputConstants.KEY_END)) {
            marked = tabs.size() - 1;
            return true;
        }
        return false;
    }

    @Override
    protected void act() {
        choose(marked);
    }

    /** Tab away and the mark goes back to the page you are actually on. */
    @Override
    protected void blurred() {
        marked = index;
    }

    private void choose(int tab) {
        marked = tab;
        if (tab != index) {
            index = tab;
            UiSounds.play(UiSounds.Cue.TAB);
            onPick.accept(index);
        }
    }

    private int[] widths() {
        int[] widths = new int[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            widths[i] = Typeset.width(Tokens.Type.MICRO, tabs.get(i)) + PAD * 2;
        }
        return widths;
    }

    /** The tab a pointer at {@code mx} is on, or -1 for a gap between two of them and for the rest
     * of the rule. */
    static int pick(double mx, int left, int[] widths, int gap) {
        int x = left;
        for (int i = 0; i < widths.length; i++) {
            if (mx >= x && mx < x + widths[i]) {
                return i;
            }
            x += widths[i] + gap;
        }
        return -1;
    }
}
