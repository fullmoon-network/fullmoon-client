package dev.fullmoon.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;
import dev.fullmoon.client.render.Painter;
import dev.fullmoon.client.text.Typeset;

/**
 * Lists: one row in all eight states and both of its selections, a well of them that scrolls, and
 * a control carrying a hint so the tooltip has somewhere to appear.
 *
 * <p>The well holds the client's own colour tokens and their packed values. A list demo filled
 * with invented names and invented numbers would prove nothing about the list or the numbers, and
 * these are the same nineteen the specimen draws as swatches — the row is another view of data
 * that is already on screen elsewhere.
 *
 * <p>Picking a row arms the copy button, which is the smallest honest thing a list can drive: it
 * puts the token on the system clipboard, and it is dead until there is something to put there.
 */
public final class ListScreen extends DevScreen {
    private static final int CELL_GAP = Tokens.Space.SNUG;

    /** Rows of the well in view at once. The rest are behind the wheel and the arrow keys. */
    private static final int WELL_ROWS = 5;

    private static final Runnable INERT = () -> {};

    private final Clipboard clipboard = Clipboard.game();

    private final ListRow plain = new ListRow("accent", hex(Tokens.Color.ACCENT), INERT);
    private final ListRow chosen = new ListRow("accent", hex(Tokens.Color.ACCENT), INERT);

    private final Button copy = new Button(Voice.LOUD, "값 복사", this::copy);
    private final ListPanel tokens = new ListPanel("색 토큰", rows(), "토큰이 없다", this::picked);

    private int picked = -1;
    private int spine;
    private int hintTop;
    private int wellTop;

    public ListScreen() {
        super(Page.LIST);
        chosen.selected(true);
        copy.enabled(false);
        copy.hint("고른 토큰의 이름과 값을 클립보드에 넣는다");
        tokens.hint("↑ ↓ 로 표시를 옮기고 Enter 로 고른다");
        surface.add(copy);
        surface.add(tokens);
    }

    @Override
    protected void lay(Box body) {
        spine = spine();
        hintTop = body.y() + sweepHeight() + Tokens.Space.GUTTER;

        int line = hintTop + DevChrome.sectionHeadHeight();
        int w = copy.measure();
        copy.place(new Box(body.right() - w, line, w, Button.HEIGHT));

        wellTop = line + Button.HEIGHT + Tokens.Space.GUTTER;
        tokens.place(new Box(body.x(), wellTop + DevChrome.sectionHeadHeight(), body.w(),
            ListPanel.heightFor(WELL_ROWS)));
    }

    @Override
    protected void paint(Painter painter, Box body) {
        sweep(painter, body);

        DevChrome.sectionHead(painter, "툴팁 · 힌트가 붙은 컨트롤에만 뜬다", body.x(), hintTop);
        int textY = Typeset.centred(Tokens.Type.BODY, copy.bounds().y(), Button.HEIGHT);
        if (picked < 0) {
            Typeset.draw(painter, Tokens.Type.BODY, "고른 항목 없음", body.x(), textY,
                Tokens.Color.INK_TERTIARY);
        } else {
            Map.Entry<String, Integer> token = Tokens.COLOR_ROLL.get(picked);
            int advance = Typeset.draw(painter, Tokens.Type.BODY, token.getKey(), body.x(), textY,
                Tokens.Color.INK_PRIMARY);
            Typeset.tabular(painter, Tokens.Type.BODY, hex(token.getValue()),
                body.x() + advance + Tokens.Space.LOOSE, textY, Tokens.Color.ACCENT);
        }

        DevChrome.sectionHead(painter, "목록 · 휠은 보이는 데를, 화살표는 표시를 옮긴다",
            body.x(), wellTop);
    }

    @Override
    protected String status() {
        Widget held = surface.held();
        return held == null ? "포커스 없음 · Tab" : "포커스 " + held.label() + " · "
            + words(surface.state(held));
    }

    /**
     * The row across every state, unselected on the left and selected on the right. Two instances
     * and not sixteen: a row is placed and drawn in the same breath, the way the kit's specimens
     * are, and neither of these is on the surface for anything to hit.
     */
    private void sweep(Painter painter, Box body) {
        int captions = DevChrome.sectionHead(painter, "행 · 여덟 상태 × 두 선택", body.x(), body.y());
        int top = captions + Tokens.Type.MICRO.leading() + Tokens.Space.SNUG;
        Box grid = Box.between(body.x() + spine, top, body.right(),
            top + State.values().length * ListRow.HEIGHT);

        String[] columns = {"안 고른 행", "고른 행"};
        for (int col = 0; col < columns.length; col++) {
            Typeset.drawCentered(painter, Tokens.Type.MICRO, columns[col],
                grid.col(col, columns.length, CELL_GAP).midX(), captions,
                Tokens.Color.INK_TERTIARY);
        }

        for (State state : State.values()) {
            int rowTop = grid.y() + state.ordinal() * ListRow.HEIGHT;
            Typeset.draw(painter, Tokens.Type.MICRO, words(state), body.x(),
                Typeset.centred(Tokens.Type.MICRO, rowTop, ListRow.HEIGHT),
                Tokens.Color.INK_TERTIARY);
            for (int col = 0; col < columns.length; col++) {
                ListRow row = col == 0 ? plain : chosen;
                Box cell = grid.col(col, columns.length, CELL_GAP);
                row.place(new Box(cell.x(), rowTop, cell.w(), ListRow.HEIGHT));
                row.draw(painter, state);
            }
        }
    }

    private void picked(int row) {
        picked = row;
        copy.enabled(true);
    }

    private void copy() {
        if (picked < 0) {
            return;
        }
        Map.Entry<String, Integer> token = Tokens.COLOR_ROLL.get(picked);
        clipboard.put(token.getKey() + " " + hex(token.getValue()));
    }

    /** The head, the column captions, then one row per state. */
    private static int sweepHeight() {
        return DevChrome.sectionHeadHeight() + Tokens.Type.MICRO.leading() + Tokens.Space.SNUG
            + State.values().length * ListRow.HEIGHT;
    }

    /** As wide as the longest state name, so no row can be pushed off its own column. */
    private static int spine() {
        int widest = 0;
        for (State state : State.values()) {
            widest = Math.max(widest, Typeset.width(Tokens.Type.MICRO, words(state)));
        }
        return widest + Tokens.Space.SECTION;
    }

    private static List<ListRow> rows() {
        List<ListRow> rows = new ArrayList<>(Tokens.COLOR_ROLL.size());
        for (Map.Entry<String, Integer> token : Tokens.COLOR_ROLL) {
            rows.add(new ListRow(token.getKey(), hex(token.getValue()), INERT));
        }
        return rows;
    }

    /** The state's own name, as words. A caption that renames the states is a caption that lies. */
    private static String words(State state) {
        return state.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** The value the renderer is actually handed, less the alpha every token carries. */
    private static String hex(int argb) {
        return Integer.toHexString(argb).substring(2).toUpperCase(Locale.ROOT);
    }
}
