package dev.postmark.client;

/** Shared packed size keeps packing, flipping and flight continuous for any paper ratio. */
public final class EnvelopeLayout {
    private EnvelopeLayout() {}
    public static double packedWidth(double ratio,int screenWidth,int screenHeight) {
        return Math.max(1,Math.min(Math.min(216,screenWidth*.44),screenHeight*.38*ratio));
    }
}
