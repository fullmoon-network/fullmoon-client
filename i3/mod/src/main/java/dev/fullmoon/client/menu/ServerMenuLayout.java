package dev.fullmoon.client.menu;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;

public record ServerMenuLayout(
        Box frame,
        Box header,
        Box actions,
        Box context,
        Box footer,
        int columns,
        int rows,
        int gap,
        int actionCount) {
    private static final int MAX_FRAME_WIDTH = 680;
    private static final int MAX_FRAME_HEIGHT = 440;
    private static final int HEADER_HEIGHT = 48;
    private static final int FOOTER_HEIGHT = 18;
    private static final int SECTION_HEAD_HEIGHT = 18;
    private static final int CONTEXT_MAX_WIDTH = 192;
    private static final int CONTEXT_MIN_WIDTH = 150;

    public ServerMenuLayout {
        if (columns < 1 || rows < 1) {
            throw new IllegalArgumentException("menu grid must have positive dimensions");
        }
        if (actionCount < 0) {
            throw new IllegalArgumentException("action count must be non-negative");
        }
    }

    public static ServerMenuLayout fit(Box viewport, int actionCount) {
        if (viewport.w() <= 0 || viewport.h() <= 0) {
            throw new IllegalArgumentException("viewport must have positive dimensions");
        }
        if (actionCount < 0) {
            throw new IllegalArgumentException("action count must be non-negative");
        }

        int edge = viewport.h() < 400 ? Tokens.Space.LOOSE : Tokens.Space.SECTION;
        int columns = columns(actionCount);
        int rows = Math.max(1, (actionCount + columns - 1) / columns);
        int gap = actionCount > 18 ? Tokens.Space.BASE : Tokens.Space.COZY;
        int cardHeight = cardHeight(actionCount);
        int deckHeight = rows * cardHeight + (rows - 1) * gap;
        int bodyHeight = SECTION_HEAD_HEIGHT + deckHeight;
        int frameHeight = Math.min(MAX_FRAME_HEIGHT, Math.min(viewport.h() - edge * 2,
            Tokens.Space.GUTTER * 2 + HEADER_HEIGHT + Tokens.Space.LOOSE
                + bodyHeight + Tokens.Space.LOOSE + FOOTER_HEIGHT));
        int frameWidth = Math.min(MAX_FRAME_WIDTH, viewport.w() - edge * 2);
        Box frame = viewport.centred(frameWidth, frameHeight);
        Box inner = frame.inset(Tokens.Space.GUTTER);
        Box header = new Box(inner.x(), inner.y(), inner.w(), HEADER_HEIGHT);
        Box footer = new Box(inner.x(), inner.bottom() - FOOTER_HEIGHT,
            inner.w(), FOOTER_HEIGHT);
        Box body = Box.between(inner.x(), header.bottom() + Tokens.Space.LOOSE,
            inner.right(), footer.y() - Tokens.Space.LOOSE);

        int contextWidth = Math.min(CONTEXT_MAX_WIDTH,
            Math.max(Math.min(CONTEXT_MIN_WIDTH, body.w() / 2), body.w() / 3));
        contextWidth = Math.min(contextWidth,
            Math.max(0, body.w() - Tokens.Space.GUTTER - 8));
        int contextX = body.right() - contextWidth;
        Box context = Box.between(contextX, body.y(), body.right(), body.bottom());
        Box actions = Box.between(body.x(), body.y() + SECTION_HEAD_HEIGHT,
            context.x() - Tokens.Space.GUTTER, body.bottom());
        // A capped frame can leave the deck shorter than its ideal; shrink the
        // gap instead of letting cells collapse to zero or escape the deck.
        gap = Math.min(gap, Math.max(0, Math.min(
            (actions.h() - rows) / Math.max(1, rows - 1),
            (actions.w() - columns) / Math.max(1, columns - 1))));
        return new ServerMenuLayout(frame, header, actions, context, footer,
            columns, rows, gap, actionCount);
    }

    public Box action(int index) {
        if (index < 0 || index >= actionCount) {
            throw new IllegalArgumentException("action index is outside the menu");
        }
        int column = index % columns;
        int row = index / columns;
        return actions.col(column, columns, gap).row(row, rows, gap);
    }

    public int sectionHeadY() {
        return actions.y() - SECTION_HEAD_HEIGHT;
    }

    private static int columns(int actionCount) {
        if (actionCount <= 8) {
            return 2;
        }
        if (actionCount <= 18) {
            return 3;
        }
        return 4;
    }

    private static int cardHeight(int actionCount) {
        if (actionCount <= 8) {
            return 56;
        }
        if (actionCount <= 18) {
            return 44;
        }
        return 36;
    }
}
