package dev.fullmoon.client.ui;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;

/**
 * The widget kit: every state of every control this client has, and the proof that eight states
 * are eight states rather than eight screenshots of the one the mouse happened to be in.
 *
 * <p>The matrix and the live band below it go through the same {@code draw(Painter, State)}. The
 * only difference is where the state comes from — the matrix hands one in, the band asks the
 * surface — so a cell that lied about a state would have to lie in the band as well.
 *
 * <p>The band is where the routing shows. 적용 puts itself in flight, 취소 takes it back out, and
 * the switch enables and disables 적용, which makes LOADING and DISABLED reachable with the mouse
 * instead of only tabulated. A control in flight keeps its focus ring and stops answering: both
 * halves of that are visible here.
 */
public final class KitScreen extends DevScreen {
    private static final int CELL_GAP = Tokens.Space.SNUG;
    private static final int ROW_H = Button.HEIGHT + Tokens.Space.COZY;
    private static final int CAPTION_LINES = captionLines();

    /** A specimen this wide takes the whole cell it lands in. */
    private static final int FILL = 0;

    private static final Runnable INERT = () -> {};
    private static final Toggle.Switched UNTHROWN = on -> {};
    private static final IntConsumer UNMOVED = v -> {};
    private static final IntConsumer UNPICKED = i -> {};
    private static final Consumer<String> UNSAID = s -> {};

    /** One line of the matrix: what it is called, what draws it, and how big that draws. */
    private record Row(String name, Widget widget, int w, int h) {}

    private final Button apply = new Button(Voice.LOUD, "적용", this::inFlight);
    private final Button cancel = new Button(Voice.QUIET, "취소", this::landed);
    private final Toggle arm = new Toggle("적용 허용", true, this::armed);

    /** A name has to be one word, which is what makes ERROR reachable by typing a space into it. */
    private final TextField name =
        new TextField("이름", "달빛", "달빛", 16, word -> !word.contains(" "), UNSAID);
    private final Select bubble =
        new Select("말풍선", List.of("표시", "이름만", "숨김"), 0, UNPICKED);
    private final Slider distance = new Slider("렌더 거리", "칸", 2, 32, 2, 12, UNMOVED);

    private final List<Row> rows = List.of(
        new Row("버튼 · quiet", new Button(Voice.QUIET, "저장", INERT), FILL, Button.HEIGHT),
        new Row("버튼 · loud", new Button(Voice.LOUD, "저장", INERT), FILL, Button.HEIGHT),
        new Row("스위치 · 꺼짐", new Toggle("", false, UNTHROWN), Toggle.TRACK_W, Toggle.TRACK_H),
        new Row("스위치 · 켜짐", new Toggle("", true, UNTHROWN), Toggle.TRACK_W, Toggle.TRACK_H),
        new Row("슬라이더", new Slider(0, 100, 5, 40, UNMOVED), FILL, Slider.KNOB),
        new Row("셀렉트", new Select("", List.of("표시", "이름만", "숨김"), 0, UNPICKED),
            FILL, Select.HEIGHT),
        new Row("텍스트 필드", new TextField("", "이름", "달빛", 16, word -> true, UNSAID),
            FILL, TextField.HEIGHT));

    private int spine;
    private int matrixTop;
    private int liveTop;

    public KitScreen() {
        super(Page.KIT);
        // Registration order is Tab order, and this one runs the band top to bottom, left to right.
        surface.add(name);
        surface.add(bubble);
        surface.add(distance);
        surface.add(arm);
        surface.add(cancel);
        surface.add(apply);
    }

    @Override
    protected void lay(Box body) {
        matrixTop = body.y();
        spine = spine();
        liveTop = bandTop() + rows.size() * ROW_H + Tokens.Space.GUTTER;

        int line = liveTop + DevChrome.sectionHeadHeight();
        Box pair = new Box(body.x(), line, body.w(), TextField.HEIGHT);
        name.place(pair.col(0, 2, Tokens.Space.LOOSE));
        bubble.place(pair.col(1, 2, Tokens.Space.LOOSE));

        line += ROW_H;
        distance.place(new Box(body.x(), line, body.w(), Slider.HEIGHT));

        line += ROW_H;
        int applyW = apply.measure();
        int cancelW = cancel.measure();
        apply.place(new Box(body.right() - applyW, line, applyW, Button.HEIGHT));
        cancel.place(new Box(body.right() - applyW - Tokens.Space.COZY - cancelW, line,
            cancelW, Button.HEIGHT));
        arm.place(new Box(body.x(), line, arm.measure(), Toggle.HEIGHT));
    }

    @Override
    protected void paint(Painter painter, Box body) {
        matrix(painter, body);
        DevChrome.sectionHead(painter, "라이브 · 적용은 요청을 띄우고 취소는 되돌린다",
            body.x(), liveTop);
    }

    @Override
    protected String status() {
        Widget held = surface.held();
        return held == null
            ? "포커스 없음 · Tab"
            : "포커스 " + held.label() + " · " + String.join(" ", caption(surface.state(held)));
    }

    private void matrix(Painter painter, Box body) {
        int captions = DevChrome.sectionHead(painter, "상태 매트릭스 · 여덟 칸 전부",
            body.x(), matrixTop);
        int top = bandTop();
        int bottom = top + rows.size() * ROW_H;
        Box grid = Box.between(body.x() + spine, captions, body.right(), bottom);
        int columns = State.values().length;

        for (State state : State.values()) {
            Box cell = grid.col(state.ordinal(), columns, CELL_GAP);
            String[] caption = caption(state);
            for (int i = 0; i < caption.length; i++) {
                Typeset.drawCentered(painter, Tokens.Type.MICRO, caption[i], cell.midX(),
                    captions + i * Tokens.Type.MICRO.leading(), Tokens.Color.INK_TERTIARY);
            }
        }
        painter.hRule(body.x(), top - Tokens.Space.TIGHT, body.w(), Tokens.Color.LINE_STRONG);
        painter.vRule(body.x() + spine - Tokens.Space.LOOSE, captions, bottom - captions,
            Tokens.Color.LINE_HAIRLINE);

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowTop = top + i * ROW_H;
            Typeset.draw(painter, Tokens.Type.MICRO, row.name(), body.x(),
                Typeset.centred(Tokens.Type.MICRO, rowTop, ROW_H), Tokens.Color.INK_TERTIARY);

            for (State state : State.values()) {
                Box cell = grid.col(state.ordinal(), columns, CELL_GAP);
                int cw = row.w() == FILL ? cell.w() - Tokens.Space.COZY : row.w();
                // A caret answers to where the keyboard is and not to the state being drawn, so the
                // two focus columns have to be told that on their own. See Widget#holding.
                row.widget().holding(state == State.FOCUS || state == State.FOCUS_VISIBLE);
                // A specimen is placed and drawn in the same breath, eight times a row. It is not
                // on the surface, so nothing hit-tests the box it was left in.
                row.widget().place(new Box(cell.midX() - cw / 2,
                    rowTop + (ROW_H - row.h()) / 2, cw, row.h()));
                row.widget().draw(painter, state);
            }
            painter.hRule(body.x(), rowTop + ROW_H, body.w(), Tokens.Color.LINE_HAIRLINE);
        }
    }

    /** The y the matrix's first row starts at: the section head, then the caption lines. */
    private int bandTop() {
        return matrixTop + DevChrome.sectionHeadHeight()
            + CAPTION_LINES * Tokens.Type.MICRO.leading() + Tokens.Space.SNUG;
    }

    private int spine() {
        int widest = 0;
        for (Row row : rows) {
            widest = Math.max(widest, Typeset.width(Tokens.Type.MICRO, row.name()));
        }
        return widest + Tokens.Space.SECTION;
    }

    /** The state's own name. {@code focus_visible} is two words, so it is two lines of caption. */
    private static String[] caption(State state) {
        return state.name().toLowerCase(Locale.ROOT).split("_");
    }

    private static int captionLines() {
        int most = 1;
        for (State state : State.values()) {
            most = Math.max(most, caption(state).length);
        }
        return most;
    }

    /** 적용 sends the whole band out, so LOADING is live for every kind of control and not only drawn. */
    private void inFlight() {
        name.busy(true);
        bubble.busy(true);
        distance.busy(true);
        apply.busy(true);
    }

    private void landed() {
        name.busy(false);
        bubble.busy(false);
        distance.busy(false);
        apply.busy(false);
    }

    private void armed(boolean on) {
        apply.enabled(on);
    }
}
