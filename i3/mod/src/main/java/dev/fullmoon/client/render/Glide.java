package dev.fullmoon.client.render;

import dev.fullmoon.client.design.Tokens;
import dev.fullmoon.client.layout.Box;

/** A rect that glides: four springs, one per edge parameter, sharing one clock. */
public final class Glide {
    private final Spring x;
    private final Spring y;
    private final Spring w;
    private final Spring h;
    private boolean placed;

    public Glide(Tokens.Spring.Shape shape) {
        x = new Spring(shape);
        y = new Spring(shape);
        w = new Spring(shape);
        h = new Spring(shape);
    }

    /** Whether the glide has ever been given a box. */
    public boolean placed() {
        return placed;
    }

    public void snap(Box box) {
        placed = true;
        x.snap(box.x());
        y.snap(box.y());
        w.snap(box.w());
        h.snap(box.h());
    }

    /** Glides toward {@code box}; the first box a glide is ever given is snapped to, not glided to. */
    public void to(Box box) {
        if (!placed) {
            snap(box);
            return;
        }
        x.to(box.x());
        y.to(box.y());
        w.to(box.w());
        h.to(box.h());
    }

    public void advance(long nowNanos) {
        x.advance(nowNanos);
        y.advance(nowNanos);
        w.advance(nowNanos);
        h.advance(nowNanos);
    }

    public boolean settled() {
        return x.settled() && y.settled() && w.settled() && h.settled();
    }

    public float x() {
        return x.value();
    }

    public float y() {
        return y.value();
    }

    public float w() {
        return w.value();
    }

    public float h() {
        return h.value();
    }

    public Box target() {
        return new Box(Math.round(x.target()), Math.round(y.target()), Math.round(w.target()), Math.round(h.target()));
    }
}
