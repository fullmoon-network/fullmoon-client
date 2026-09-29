package dev.fullmoon.client.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;

/**
 * The design specimen: every token this client owns, drawn by the client's own renderer.
 *
 * <p>It is a working surface, not a demo. Each block is here because something downstream
 * depends on it being right — the type roll proves the five baked providers load and that
 * their leadings stack without collision, the shape rail proves one SDF pipeline covers every
 * solid the UI needs, the colour bands prove the generated constants are the ones on screen,
 * and the figure pair proves tabular digits hold still while proportional ones do not.
 *
 * <p>The only page with nothing to click. That is not an omission — a token is not a control,
 * and giving the swatches a hover state to make the page feel alive would be inventing an
 * affordance that leads nowhere. What moves here is the frame counter, and it moves because the
 * world behind the blur is still running.
 */
public final class SpecimenScreen extends DevScreen {
    private static final int LEFT_PERCENT = 62;
    private static final String[] SAMPLES = {"달빛 Fullmoon 0123", "달빛 Fullmoon", "달빛"};

    private int frames;

    public SpecimenScreen() {
        super(Page.SPECIMEN);
    }

    /** Two columns of specimen, which is narrower than a page of controls wants to be. */
    @Override
    protected int maxContent() {
        return 420;
    }

    /** Nothing to place: the rail the chrome owns is the whole of this page's surface. */
    @Override
    protected void lay(Box body) {
    }

    @Override
    protected void paint(Painter painter, Box body) {
        frames++;
        int leftW = body.w() * LEFT_PERCENT / 100;
        int rightX = body.x() + leftW + Tokens.Space.GUTTER;
        int rightW = body.w() - leftW - Tokens.Space.GUTTER;

        int leftBottom = shapeRail(painter, body.x(),
            typeRoll(painter, body.x(), body.y(), leftW) + Tokens.Space.SECTION, leftW);
        int rightBottom = figures(painter, rightX,
            colorBands(painter, rightX, body.y(), rightW) + Tokens.Space.SECTION, rightW);
        rightBottom = glass(painter, rightX, rightBottom + Tokens.Space.SECTION, rightW);

        painter.vRule(body.x() + leftW + Tokens.Space.GUTTER / 2, body.y(),
            Math.max(leftBottom, rightBottom) - body.y(), Tokens.Color.LINE_HAIRLINE);
    }

    /** What the page is for, counted: a footer that says the roll sizes is a footer that checks them. */
    @Override
    protected String status() {
        return "색 " + Tokens.COLOR_ROLL.size() + " · 타입 " + Tokens.Type.ROLL.size();
    }

    private static int typeRoll(Painter painter, int x, int y, int w) {
        int cursor = DevChrome.sectionHead(painter, "타입 스케일", x, y);
        int nameCol = nameColumn();
        int metricW = Typeset.tabularWidth(Tokens.Type.MICRO, "22/28") + Tokens.Space.LOOSE;
        int sampleW = w - nameCol - metricW;

        for (Map.Entry<String, Tokens.Type.Role> entry : Tokens.Type.ROLL) {
            Tokens.Type.Role role = entry.getValue();
            int rowH = role.leading() + Tokens.Space.SNUG;
            // Name, sample and metric share the sample's baseline, so a row reads as one line
            // however large the face in it is.
            int textY = Typeset.centred(role, cursor, rowH);

            Typeset.draw(painter, Tokens.Type.MICRO, entry.getKey(), x, textY, Tokens.Color.INK_TERTIARY);

            // The column is clipped, not the row: the game hangs every face off one 9 px line
            // box, so a 22 px sample legitimately draws taller than the band it is measured in,
            // and cutting its ascenders would misrepresent the thing the row exists to show.
            painter.pushClip(x + nameCol, 0, sampleW, painter.height());
            Typeset.draw(painter, role, sample(role, sampleW), x + nameCol, textY, Tokens.Color.INK_PRIMARY);
            painter.popClip();

            Typeset.tabularRight(painter, Tokens.Type.MICRO, role.px() + "/" + role.leading(),
                x + w, textY, Tokens.Color.INK_TERTIARY);

            cursor += rowH;
            painter.hRule(x, cursor, w, Tokens.Color.LINE_HAIRLINE);
            cursor += Tokens.Space.SNUG;
        }
        return cursor;
    }

    /**
     * The longest sample the role can set inside the column. A foundry shows a display face on
     * fewer characters for the same reason: the row exists to show the shape of the glyphs, and
     * a face cut off mid-stroke shows nothing. The clip stays as the guarantee.
     */
    private static String sample(Tokens.Type.Role role, int w) {
        for (String candidate : SAMPLES) {
            if (Typeset.width(role, candidate) <= w) {
                return candidate;
            }
        }
        return SAMPLES[SAMPLES.length - 1];
    }

    /** The name column is as wide as the longest role name, so no sample can be pushed into it. */
    private static int nameColumn() {
        int widest = 0;
        for (Map.Entry<String, Tokens.Type.Role> entry : Tokens.Type.ROLL) {
            widest = Math.max(widest, Typeset.width(Tokens.Type.MICRO, entry.getKey()));
        }
        return widest + Tokens.Space.LOOSE;
    }

    /** Six chips, one pipeline: the fill, the three radii, the inset stroke, the ring and dot. */
    private static int shapeRail(Painter painter, int x, int y, int w) {
        int cursor = DevChrome.sectionHead(painter, "형상 · 하나의 SDF 파이프라인", x, y);
        String[] captions = {"none", "sm", "md", "lg", "1px", "ring"};
        int gap = Tokens.Space.COZY;
        int cellW = (w - gap * (captions.length - 1)) / captions.length;
        int cellH = 22;

        for (int i = 0; i < captions.length; i++) {
            int cx = x + i * (cellW + gap);
            switch (i) {
                case 0 -> painter.fill(cx, cursor, cellW, cellH, Tokens.Radius.NONE, Tokens.Color.SURFACE_RAISED);
                case 1 -> painter.fill(cx, cursor, cellW, cellH, Tokens.Radius.SM, Tokens.Color.SURFACE_RAISED);
                case 2 -> painter.fill(cx, cursor, cellW, cellH, Tokens.Radius.MD, Tokens.Color.SURFACE_RAISED);
                case 3 -> painter.fill(cx, cursor, cellW, cellH, Tokens.Radius.LG, Tokens.Color.ACCENT_WASH);
                case 4 -> painter.border(cx, cursor, cellW, cellH, Tokens.Radius.MD,
                    Tokens.Stroke.HAIR, Tokens.Color.LINE_STRONG);
                default -> {
                    float mid = cursor + cellH / 2.0f;
                    painter.ring(cx + cellW / 2.0f, mid, cellH / 2.0f - 1.0f, Tokens.Stroke.FOCUS, Tokens.Color.ACCENT);
                    painter.dot(cx + cellW / 2.0f, mid, Tokens.Space.SNUG, Tokens.Color.ACCENT);
                }
            }
            Typeset.drawCentered(painter, Tokens.Type.MICRO, captions[i],
                cx + cellW / 2, cursor + cellH + Tokens.Space.SNUG, Tokens.Color.INK_TERTIARY);
        }
        return cursor + cellH + Tokens.Space.SNUG + Tokens.Type.MICRO.leading();
    }

    /**
     * The glass vocabulary on one small panel — a header with its way back and out, a chosen row,
     * a keycap, an action row, a reason line — then the moon at five phases. The phases are the
     * ones the casino and the clock reach, so a terminator that bends the wrong way shows up here
     * first.
     */
    private static int glass(Painter painter, int x, int y, int w) {
        int cursor = DevChrome.sectionHead(painter, "유리 · 달", x, y);
        Box panel = new Box(x + 1, cursor + 1, w - 2, 96);
        Glass.panel(painter, panel);
        int header = Tokens.Size.HEADER;
        Glass.back(painter, panel.x() + 10, panel.y() + header / 2.0f, 8, Tokens.Color.INK_TERTIARY);
        Typeset.draw(painter, Tokens.Type.TITLE, "만월", panel.x() + 18,
            Typeset.centred(Tokens.Type.TITLE, panel.y(), header), Tokens.Color.INK_PRIMARY);
        Glass.close(painter, panel.right() - 14, panel.y() + header / 2.0f, 7, Tokens.Color.INK_TERTIARY);
        Glass.keycap(painter, panel.right() - 24 - Glass.keycapWidth("Esc"), panel.y() + (header - Tokens.Size.KEYCAP) / 2, "Esc");
        Glass.hair(painter, panel.x(), panel.y() + header, panel.w());

        int rowY = panel.y() + header;
        Box chosen = new Box(panel.x(), rowY, panel.w(), Tokens.Size.ROW_ONE);
        painter.fill(chosen.x(), chosen.y(), chosen.w(), chosen.h(), Tokens.Color.ACCENT_WASH);
        painter.fill(chosen.x(), chosen.y(), Tokens.Stroke.BAR, chosen.h(), Tokens.Color.ACCENT);
        Typeset.draw(painter, Tokens.Type.ROW, "선택한 줄", chosen.x() + Tokens.Space.LOOSE,
            Typeset.centred(Tokens.Type.ROW, chosen.y(), chosen.h()), Tokens.Color.INK_PRIMARY);
        Box other = new Box(panel.x(), chosen.bottom(), panel.w(), Tokens.Size.ROW_ONE);
        painter.fill(other.x(), other.y(), other.w(), other.h(), Tokens.Color.SURFACE_RAISED);
        Typeset.draw(painter, Tokens.Type.ROW, "가리킨 줄", other.x() + Tokens.Space.LOOSE,
            Typeset.centred(Tokens.Type.ROW, other.y(), other.h()), Tokens.Color.INK_PRIMARY);

        int foot = other.bottom() + Tokens.Space.SNUG;
        Glass.actionRow(painter, new Box(panel.x() + Tokens.Space.LOOSE, foot,
            panel.w() / 2 - Tokens.Space.LOOSE, Tokens.Size.ACTION_ROW), false, "고르기", 1.0f);
        Glass.markedLine(painter, panel.midX() + Tokens.Space.COZY,
            Typeset.centred(Tokens.Type.STRONG, foot, Tokens.Size.ACTION_ROW), "잔액이 부족해요",
            Tokens.Color.STATUS_DANGER, true);
        cursor = panel.bottom() + Tokens.Space.LOOSE;

        float[] phases = {0.08f, 0.3f, 0.5f, 0.75f, 1.0f};
        float r = 9.0f;
        int cell = w / phases.length;
        for (int i = 0; i < phases.length; i++) {
            float cx = x + cell * i + cell / 2.0f;
            painter.moon(cx, cursor + r, r, phases[i], true, Tokens.Color.MOON_LIT, Tokens.Color.MOON_SHADOW);
            Typeset.drawCentered(painter, Tokens.Type.MICRO, Math.round(phases[i] * 100) + "%",
                Math.round(cx), (int) (cursor + r * 2 + Tokens.Space.SNUG), Tokens.Color.INK_TERTIARY);
        }
        return (int) (cursor + r * 2 + Tokens.Space.SNUG + Tokens.Type.MICRO.leading());
    }

    private static int colorBands(Painter painter, int x, int y, int w) {
        int cursor = DevChrome.sectionHead(painter, "색 · OKLCH 토큰", x, y);
        Map<String, List<Integer>> families = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : Tokens.COLOR_ROLL) {
            int dot = entry.getKey().indexOf('.');
            String family = dot < 0 ? entry.getKey() : entry.getKey().substring(0, dot);
            families.computeIfAbsent(family, key -> new ArrayList<>()).add(entry.getValue());
        }

        int bandH = 14;
        int nameCol = 0;
        for (String family : families.keySet()) {
            nameCol = Math.max(nameCol, Typeset.width(Tokens.Type.MICRO, family));
        }
        nameCol += Tokens.Space.COZY;

        for (Map.Entry<String, List<Integer>> family : families.entrySet()) {
            Typeset.draw(painter, Tokens.Type.MICRO, family.getKey(), x,
                Typeset.centred(Tokens.Type.MICRO, cursor, bandH), Tokens.Color.INK_TERTIARY);

            List<Integer> swatches = family.getValue();
            int gap = Tokens.Space.TIGHT;
            int bandsW = w - nameCol;
            int cellW = (bandsW - gap * (swatches.size() - 1)) / swatches.size();
            for (int i = 0; i < swatches.size(); i++) {
                int sx = x + nameCol + i * (cellW + gap);
                painter.fill(sx, cursor, cellW, bandH, Tokens.Radius.SM, swatches.get(i));
                // The darkest surfaces would otherwise be invisible against the scrim.
                painter.border(sx, cursor, cellW, bandH, Tokens.Radius.SM,
                    Tokens.Stroke.HAIR, Tokens.Color.LINE_HAIRLINE);
            }
            cursor += bandH + Tokens.Space.COZY;
        }
        return cursor;
    }

    /** The same live value set twice. The right column is the one that jitters. */
    private int figures(Painter painter, int x, int y, int w) {
        int cursor = DevChrome.sectionHead(painter, "숫자 · 고정폭", x, y);
        String live = String.format("%06d", frames);
        int half = w / 2;

        Typeset.draw(painter, Tokens.Type.MICRO, "고정폭", x, cursor, Tokens.Color.INK_TERTIARY);
        Typeset.draw(painter, Tokens.Type.MICRO, "비례", x + half, cursor, Tokens.Color.INK_TERTIARY);
        cursor += Tokens.Type.MICRO.leading() + Tokens.Space.TIGHT;

        Typeset.tabular(painter, Tokens.Type.STRONG, live, x, cursor, Tokens.Color.ACCENT);
        Typeset.draw(painter, Tokens.Type.STRONG, live, x + half, cursor, Tokens.Color.INK_SECONDARY);
        return cursor + Tokens.Type.STRONG.leading();
    }
}
