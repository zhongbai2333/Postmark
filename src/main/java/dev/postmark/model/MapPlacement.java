package dev.postmark.model;

/** Same x/z mapping as ExhibitionPortal's MapLayouts.ME_UI. */
public final class MapPlacement {
    private MapPlacement() {}
    /** SMU draws the player texture in a 30-pixel square; match the visible alpha bounds + 15%. */
    public static double playerSizedRatio(double mapWidth,double visibleFraction) {
        if(!Double.isFinite(mapWidth) || mapWidth<=0 || !Double.isFinite(visibleFraction) || visibleFraction<=0 || visibleFraction>1)
            throw new IllegalArgumentException("Invalid player marker size");
        return Math.min(.25,30*visibleFraction*1.15/mapWidth);
    }
    public record Rect(float x, float y, float width, float height) {}
    public static Rect at(double playerX, double playerZ, double x0, double z0,
                          double worldWidth, double worldHeight, double mapAspect, double size) {
        if (!Double.isFinite(worldWidth) || !Double.isFinite(worldHeight)
                || worldWidth == 0 || worldHeight == 0 || !Double.isFinite(mapAspect)
                || mapAspect <= 0 || !Double.isFinite(size) || size <= 0 || size > .25)
            throw new IllegalArgumentException("Invalid map bounds");
        double u = (playerX - x0) / worldWidth, v = (playerZ - z0) / worldHeight;
        if (!Double.isFinite(u) || !Double.isFinite(v) || u < 0 || u > 1 || v < 0 || v > 1)
            throw new IllegalArgumentException("Player is outside the map");
        double h = size * mapAspect;
        // Keep the centre at the actual location. Edge stamps may be clipped by the map.
        return new Rect((float)(u - size / 2), (float)(v - h / 2), (float)size, (float)h);
    }
}