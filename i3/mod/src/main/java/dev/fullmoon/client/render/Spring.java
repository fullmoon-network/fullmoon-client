package dev.fullmoon.client.render;

import dev.fullmoon.client.design.Tokens;

/**
 * A damped spring on one value.
 *
 * <p>It is advanced analytically rather than by Euler steps, so any sequence of frame times lands
 * on the same curve: sixteen 10 ms frames and one 160 ms frame put the value in the same place,
 * and a frame that took too long cannot make it overshoot. Retargeting keeps the current value
 * and velocity, so a highlight already gliding turns toward the new stop without a hitch, and a
 * fast key repeat lands its last target instantly in state — only the drawn position lags, and
 * by no more than the spring's response.
 */
public final class Spring {
    private static final float SETTLE_DISTANCE = 0.01f;
    private static final float SETTLE_SPEED = 1.0f;

    private final float omega;
    private final float zeta;
    private float value;
    private float velocity;
    private float target;
    private long lastNanos;
    private boolean timed;

    public Spring(Tokens.Spring.Shape shape) {
        this(shape.response(), shape.dampingFraction());
    }

    /** {@code response} is the undamped period in seconds; {@code dampingFraction} 1 never overshoots. */
    public Spring(float response, float dampingFraction) {
        if (response <= 0.0f) {
            throw new IllegalArgumentException("response must be positive");
        }
        this.omega = (float) (2.0 * Math.PI / response);
        this.zeta = Math.clamp(dampingFraction, 0.05f, 1.0f);
    }

    /** Puts the spring at rest on {@code at}. */
    public void snap(float at) {
        value = at;
        target = at;
        velocity = 0.0f;
    }

    /** Aims the spring at {@code next}, keeping where it is and how fast it is moving. */
    public void to(float next) {
        if (Motion.reduced()) {
            snap(next);
            return;
        }
        target = next;
    }

    public float target() {
        return target;
    }

    public float value() {
        return value;
    }

    public float velocity() {
        return velocity;
    }

    public boolean settled() {
        return Math.abs(value - target) < SETTLE_DISTANCE && Math.abs(velocity) < SETTLE_SPEED;
    }

    /** Advances to {@code nowNanos} from the last call; the first call only starts the clock. */
    public float advance(long nowNanos) {
        if (timed) {
            step((nowNanos - lastNanos) / 1_000_000_000.0f);
        }
        timed = true;
        lastNanos = nowNanos;
        return value;
    }

    /** Advances by {@code dt} seconds along the closed-form solution. */
    public void step(float dt) {
        if (dt <= 0.0f) {
            return;
        }
        if (settled()) {
            value = target;
            velocity = 0.0f;
            return;
        }
        float d = value - target;
        if (zeta >= 1.0f) {
            float a = d;
            float b = velocity + omega * d;
            float decay = (float) Math.exp(-omega * dt);
            value = target + (a + b * dt) * decay;
            velocity = (b - omega * (a + b * dt)) * decay;
        } else {
            float damped = omega * (float) Math.sqrt(1.0 - zeta * zeta);
            float a = d;
            float c = (velocity + zeta * omega * d) / damped;
            float decay = (float) Math.exp(-zeta * omega * dt);
            float cos = (float) Math.cos(damped * dt);
            float sin = (float) Math.sin(damped * dt);
            value = target + decay * (a * cos + c * sin);
            velocity = decay * (-zeta * omega * (a * cos + c * sin) + damped * (-a * sin + c * cos));
        }
        if (settled()) {
            value = target;
            velocity = 0.0f;
        }
    }
}
