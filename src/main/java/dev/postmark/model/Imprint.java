package dev.postmark.model;

import java.util.UUID;

/** A committed mark is immutable. Moving it means erasing and stamping again. */
public record Imprint(UUID id, String source, String asset, double x, double y,
                      double size, double angle) {
    public Imprint {
        if (id == null || source == null || asset == null
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(angle)
                || x < 0 || x > 1 || y < 0 || y > 1 || !Double.isFinite(size)
                || size < .02 || size > .5) throw new IllegalArgumentException("Invalid imprint");
    }
    public boolean contains(double px, double py) {
        // Work in postcard-width units; the card aspect ratio is 3:2.
        double dx = px - x, dy = (py - y) / 1.5;
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return Math.abs(dx * cos + dy * sin) <= size / 2
                && Math.abs(-dx * sin + dy * cos) <= size / 2;
    }
}