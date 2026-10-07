package dev.fullmoon.client.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.fonts.FontLoading;
import dev.fullmoon.client.text.fonts.GuiScaleVariants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * The client's text layer: role in, glyphs out.
 *
 * <p>Three things the game will not do for us live here. It draws UI text with a hard drop
 * shadow by default, which is the one visual habit that marks a screen as vanilla chrome, so
 * every call in this class passes {@code shadow = false}. Its proportional digits jitter a live
 * counter by a pixel or two per frame, so {@link #tabular} lays digits out on a fixed cell — the
 * widest digit in the role — and leaves everything else on its natural advance. And it
 * rasterises a ttf provider once, at one oversample, then samples the atlas with nearest
 * filtering: a glyph drawn at any other GUI scale is a resampled bitmap. So every role is baked
 * once per GUI scale, and {@link #style} picks the provider whose oversample is the scale the
 * window is at, which is what puts one atlas texel on one screen pixel.
 *
 * <p>{@code Font.lineHeight} is the constant 9 for every font in the game, including ours, so
 * vertical rhythm comes from {@link Tokens.Type.Role#leading()} and never from the font.
 */
public final class Typeset {
    /**
     * Distance from a draw origin to the baseline the glyphs actually sit on: the ascent of the
     * game's one 9 px line box. It does not scale with the provider, so a 20 px face draws well
     * above its origin and a band that wants a face centred in it has to work from here.
     */
    private static final int ASCENT = 7;
    private static final String ELLIPSIS = "…";

    /** The GUI scales a provider set is baked for; any other scale takes the nearest of these. */
    private static final int[] SCALES = GuiScaleVariants.SCALES;

    /** Strings kept per face before its table is dropped; a screen of live text is a few hundred. */
    private static final int MEMO_LIMIT = 2048;

    private static final Map<String, Style> STYLES = new HashMap<>();
    private static final Map<Tokens.Type.Role, String[]> FONT_IDS = new IdentityHashMap<>();
    private static final Map<Tokens.Type.Role, Style[]> ROLE_STYLES = new IdentityHashMap<>();
    private static final Map<Style, Face> FACES = new IdentityHashMap<>();
    private static int epoch;
    private static int warmedIndex = -1;
    /**
     * How far under the middle of its line box a face's baseline sits, as a share of the em:
     * (ascender − descender) / 2 in the face's own metrics, which is where the mockups' CSS line
     * boxes put it. Measured on the title, the menus and the route against their renders:
     * 0.34–0.36 em for Pretendard, 0.39–0.43 em for Hahmlet.
     */
    private static final float SANS_BASELINE_DROP = 0.35f;
    private static final float SERIF_BASELINE_DROP = 0.41f;

    private Typeset() {}

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    /** The provider id a role draws through at the window's GUI scale. */
    public static String fontId(Tokens.Type.Role role) {
        return fontId(role, Minecraft.getInstance().getWindow().getGuiScale());
    }

    /** {@code fullmoon:body_x3} for body at scale 3; scale 1 shares the ×2 atlas, 5 and up the ×4. */
    public static String fontId(Tokens.Type.Role role, int guiScale) {
        String[] ids = FONT_IDS.get(role);
        if (ids == null) {
            ids = new String[SCALES.length];
            for (int i = 0; i < SCALES.length; i++) {
                ids[i] = role.font() + "_x" + SCALES[i];
            }
            FONT_IDS.put(role, ids);
        }
        int index = scaleIndex(guiScale);
        if (index != warmedIndex) {
            // The window moved to another scale: the providers baked for it are opened on a
            // worker, because the game only opens the scale it started at.
            warmedIndex = index;
            FontLoading.warm(guiScale);
        }
        return ids[index];
    }

    private static int scaleIndex(int guiScale) {
        return GuiScaleVariants.indexOf(guiScale);
    }

    /**
     * The role a string is actually set in. Hangul is never set below 9 px: a role marked Latin
     * only hands a string with any Hangul in it to {@link Tokens.Type#STRONG}, the same weight one
     * size up, so a hint that gains a Korean word never comes out smaller than the body it sits by.
     */
    public static Tokens.Type.Role roleFor(Tokens.Type.Role role, String text) {
        return role.latinOnly() && hasHangul(text) ? Tokens.Type.STRONG : role;
    }

    /** Whether any code point is Hangul: syllables, jamo, or compatibility jamo. */
    public static boolean hasHangul(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x1100 && c <= 0x11FF) || (c >= 0x3130 && c <= 0x318F)) {
                return true;
            }
        }
        return false;
    }

    private static Style style(Tokens.Type.Role role) {
        return style(role, Minecraft.getInstance().getWindow().getGuiScale());
    }

    private static Style style(Tokens.Type.Role role, int guiScale) {
        Style[] row = ROLE_STYLES.get(role);
        if (row == null) {
            row = new Style[SCALES.length];
            ROLE_STYLES.put(role, row);
        }
        int index = scaleIndex(guiScale);
        Style style = row[index];
        if (style == null) {
            style = styleOf(fontId(role, guiScale));
            row[index] = style;
        }
        return style;
    }

    private static Style styleOf(String fontId) {
        return STYLES.computeIfAbsent(fontId,
            id -> Style.EMPTY.withFont(new FontDescription.Resource(Identifier.parse(id))));
    }

    /**
     * The role's text through the provider baked for {@code guiScale}, whatever scale the window
     * is at. Only the specimen asks: it draws one line through every atlas so the difference
     * between a matched oversample and a resampled one can be photographed.
     */
    public static Component sayAt(Tokens.Type.Role role, int guiScale, String text) {
        return Component.literal(text).withStyle(styleOf(fontId(roleFor(role, text), guiScale)));
    }

    /** The role's text as a component, for the game's own text and tooltip APIs. */
    public static Component say(Tokens.Type.Role role, String text) {
        return shaped(style(roleFor(role, text)), text).component();
    }

    public static int width(Tokens.Type.Role role, String text) {
        return shaped(style(roleFor(role, text)), text).width();
    }

    /** A server's own text set in the role's face, keeping the colours and styles it carries. */
    public static Component restyle(Tokens.Type.Role role, Component text) {
        return Component.empty().withStyle(style(roleFor(role, text.getString()))).append(text);
    }

    public static int width(Tokens.Type.Role role, Component text) {
        return font().width(restyle(role, text));
    }

    /** Draws server text left-aligned; {@code color} is only for the parts it leaves uncoloured. */
    public static int draw(Painter painter, Tokens.Type.Role role, Component text, int x, int y, int color) {
        Component styled = restyle(role, text);
        painter.gfx().nextStratum();
        painter.gfx().text(font(), styled, x, y, painter.tint(color), false);
        painter.gfx().nextStratum();
        return font().width(styled);
    }

    /** The longest complete-code-point prefix that fits inside {@code width}. */
    public static String fittingPrefix(Tokens.Type.Role role, String text, int width) {
        return fittingPrefix(t -> width(role, t), text, width);
    }

    /** The text cut to fit {@code width}, ending in an ellipsis when anything was cut. */
    public static String ellipsized(Tokens.Type.Role role, String text, int width) {
        return ellipsized(t -> width(role, t), text, width);
    }

    /**
     * The text broken into at most {@code maxLines} lines no wider than {@code width}: at a space
     * where one fits, inside a word where none does. A last line that had to be cut ends in an
     * ellipsis, so nothing is ever silently clipped.
     */
    public static List<String> lines(Tokens.Type.Role role, String text, int width, int maxLines) {
        return lines(t -> width(role, t), text, width, maxLines);
    }

    /** Prefix widths never shrink as a prefix grows, so the longest one that fits is found by halving. */
    static String fittingPrefix(ToIntFunction<String> measure, String text, int width) {
        int low = 0;
        int high = text.codePointCount(0, text.length());
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (measure.applyAsInt(text.substring(0, text.offsetByCodePoints(0, mid))) > width) {
                high = mid - 1;
            } else {
                low = mid;
            }
        }
        return text.substring(0, text.offsetByCodePoints(0, low));
    }

    static String ellipsized(ToIntFunction<String> measure, String text, int width) {
        if (measure.applyAsInt(text) <= width) {
            return text;
        }
        return fittingPrefix(measure, text, width - measure.applyAsInt(ELLIPSIS)).stripTrailing() + ELLIPSIS;
    }

    static List<String> lines(ToIntFunction<String> measure, String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        String rest = text.strip();
        while (!rest.isEmpty() && lines.size() < maxLines) {
            if (lines.size() == maxLines - 1) {
                lines.add(ellipsized(measure, rest, width));
                break;
            }
            String fit = fittingPrefix(measure, rest, width);
            if (fit.length() == rest.length()) {
                lines.add(rest);
                break;
            }
            int space = fit.lastIndexOf(' ');
            int cut = space > 0 ? space : Math.max(fit.length(), Character.charCount(rest.codePointAt(0)));
            lines.add(rest.substring(0, cut).stripTrailing());
            rest = rest.substring(cut).stripLeading();
        }
        return lines;
    }

    /** Draws left-aligned from the text's top-left corner. */
    public static int draw(Painter painter, Tokens.Type.Role role, String text, int x, int y, int color) {
        Shaped shaped = shaped(style(roleFor(role, text)), text);
        painter.gfx().nextStratum();
        painter.gfx().text(font(), shaped.component(), x, y, painter.tint(color), false);
        painter.gfx().nextStratum();
        return shaped.width();
    }

    /** Draws right-aligned so that the text ends at {@code right}. */
    public static int drawRight(Painter painter, Tokens.Type.Role role, String text, int right, int y, int color) {
        int w = width(role, text);
        draw(painter, role, text, right - w, y, color);
        return w;
    }

    /** Draws centred on {@code cx}. */
    public static int drawCentered(Painter painter, Tokens.Type.Role role, String text, int cx, int y, int color) {
        int w = width(role, text);
        draw(painter, role, text, cx - w / 2, y, color);
        return w;
    }

    /** Draws at most {@code maxLines} on the role's leading and returns the height it used. */
    public static int drawWrapped(Painter painter, Tokens.Type.Role role, String text, int x, int y,
            int width, int maxLines, int color) {
        Tokens.Type.Role set = roleFor(role, text);
        java.util.List<FormattedCharSequence> lines = font().split(say(set, text), width);
        int shown = Math.min(maxLines, lines.size());
        painter.gfx().nextStratum();
        for (int i = 0; i < shown; i++) {
            painter.gfx().text(font(), lines.get(i), x, y + i * set.leading(), painter.tint(color), false);
        }
        painter.gfx().nextStratum();
        return shown * set.leading();
    }

    /**
     * The draw origin that puts a role's text where a CSS line box centred in a band {@code h}
     * tall, starting at {@code top}, would put it: the baseline {@link #baselineDrop} under the
     * band's middle.
     */
    public static int centred(Tokens.Type.Role role, int top, int h) {
        return top + h / 2 + baselineDrop(role) - ASCENT;
    }

    /** Whole GUI pixels from the middle of a role's line box down to its baseline. */
    static int baselineDrop(Tokens.Type.Role role) {
        boolean serif = role == Tokens.Type.MARK || role == Tokens.Type.DISPLAY || role == Tokens.Type.TITLE;
        return Math.round(role.px() * (serif ? SERIF_BASELINE_DROP : SANS_BASELINE_DROP));
    }

    /** The origin to draw at so the glyphs sit on {@code baseline}, whatever the face. */
    public static int originFor(int baseline) {
        return baseline - ASCENT;
    }

    /**
     * The height of a role's capitals: the cap line down to the baseline. Same model as
     * {@link #centred} — a face carries about a quarter of its size below the baseline and the
     * rest of it above.
     */
    public static int capHeight(Tokens.Type.Role role) {
        return role.px() - role.px() / 4;
    }

    /**
     * The y a role's capitals begin at when it is drawn at origin {@code y}. A rule that stands
     * beside a wordmark has to start here and not at the origin: the line box is 9 px whatever
     * the face is, so a large role draws most of its body above the origin it was handed.
     */
    public static int capTop(Tokens.Type.Role role, int y) {
        return y + ASCENT - capHeight(role);
    }

    /** The advance of the widest digit in the role — one column of a tabular figure. */
    public static float digitCell(Tokens.Type.Role role) {
        return face(style(role)).cell();
    }

    /** Advance of {@code text} once digits are forced onto the tabular cell. */
    public static int tabularWidth(Tokens.Type.Role role, String text) {
        return Math.round(tab(style(roleFor(role, text)), text).width());
    }

    /**
     * Draws with digits on a fixed cell, centred in it the way a tabular face cuts its own
     * figures — a narrow 1 pushed to one side of the cell reads as a word space. Use this
     * for anything that changes while the player is looking at it: counters, coordinates,
     * timers, ping.
     *
     * <p>The letters between the digits are drawn as runs on fractional advances: one glyph
     * per draw call would round every advance up to a whole pixel and track a thirty-letter
     * line a dozen pixels wide.
     */
    public static int tabular(Painter painter, Tokens.Type.Role role, String text, int x, int y, int color) {
        return Math.round(tabular(painter, role, text, (float) x, y, color));
    }

    /** The tabular layout, drawn from the segments {@link #tab} measured the first time it saw the text. */
    private static float tabular(Painter painter, Tokens.Type.Role role, String text, float x, int y, int color) {
        Tab tab = tab(style(roleFor(role, text)), text);
        float cursor = x;
        int tint = painter.tint(color);
        painter.gfx().nextStratum();
        for (int i = 0; i < tab.segments.length; i++) {
            Segment segment = tab.segments[i];
            if (segment.digit) {
                painter.gfx().text(font(), segment.component,
                    Math.round(cursor + (tab.cell - segment.advance) / 2), y, tint, false);
                cursor += tab.cell;
            } else {
                painter.gfx().text(font(), segment.component, Math.round(cursor), y, tint, false);
                cursor += segment.advance;
            }
        }
        painter.gfx().nextStratum();
        return cursor - x;
    }

    /** As {@link #tabular}, ending at {@code right}. Keeps a changing value's last digit still. */
    public static int tabularRight(Painter painter, Tokens.Type.Role role, String text, int right, int y, int color) {
        int w = tabularWidth(role, text);
        tabular(painter, role, text, right - w, y, color);
        return w;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** Bumps whenever {@link #invalidate} drops the metrics, so a width kept elsewhere knows it is stale. */
    public static int epoch() {
        return epoch;
    }

    /** Drops the memoised metrics. Called on a resource reload, when the atlases change. */
    public static void invalidate() {
        FACES.clear();
        ROLE_STYLES.clear();
        STYLES.clear();
        epoch++;
    }

    private static Face face(Style style) {
        Face face = FACES.get(style);
        if (face == null) {
            face = new Face(style);
            FACES.put(style, face);
        }
        return face;
    }

    private static Shaped shaped(Style style, String text) {
        Face face = face(style);
        Shaped shaped = face.texts.find(text);
        if (shaped == null) {
            Component component = Component.literal(text).withStyle(style);
            shaped = face.texts.remember(text, new Shaped(component, font().width(component)));
        }
        return shaped;
    }

    private static Tab tab(Style style, String text) {
        Face face = face(style);
        Tab tab = face.tabs.find(text);
        if (tab == null) {
            tab = face.tabs.remember(text, face.layout(text));
        }
        return tab;
    }

    /** The unrounded advance of {@code text} in a face. */
    private static float advance(Style style, String text) {
        return font().getSplitter().stringWidth(Component.literal(text).withStyle(style));
    }

    /** A string as a component and the whole pixels it measures, kept so neither is rebuilt per frame. */
    private record Shaped(Component component, int width) {}

    /** One stretch of a tabular line: a single digit, or the run of anything else between digits. */
    private record Segment(Component component, float advance, boolean digit) {}

    /** A tabular line broken into segments once; {@code width} is its advance laid out from zero. */
    private record Tab(Segment[] segments, float cell, float width) {}

    /** What is memoised for one provider's style: the strings set in it, and its digit metrics. */
    private static final class Face {
        private final Style style;
        private final Memo<Shaped> texts = new Memo<>(MEMO_LIMIT);
        private final Memo<Tab> tabs = new Memo<>(MEMO_LIMIT);
        private float[] digitAdvances;
        private float cell;

        private Face(Style style) {
            this.style = style;
        }

        private float cell() {
            digits();
            return cell;
        }

        private float[] digits() {
            if (digitAdvances == null) {
                float[] advances = new float[10];
                float widest = 0;
                for (int digit = 0; digit < 10; digit++) {
                    advances[digit] = advance(style, String.valueOf((char) ('0' + digit)));
                    widest = Math.max(widest, advances[digit]);
                }
                digitAdvances = advances;
                cell = widest;
            }
            return digitAdvances;
        }

        private Tab layout(String text) {
            float[] advances = digits();
            List<Segment> segments = new ArrayList<>();
            float cursor = 0;
            int i = 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (isDigit(c)) {
                    segments.add(new Segment(
                        Component.literal(String.valueOf(c)).withStyle(style), advances[c - '0'], true));
                    cursor += cell;
                    i++;
                    continue;
                }
                int end = i;
                while (end < text.length() && !isDigit(text.charAt(end))) {
                    end++;
                }
                String run = text.substring(i, end);
                float advance = advance(style, run);
                segments.add(new Segment(Component.literal(run).withStyle(style), advance, false));
                cursor += advance;
                i = end;
            }
            return new Tab(segments.toArray(new Segment[0]), cell, cursor);
        }
    }
}
