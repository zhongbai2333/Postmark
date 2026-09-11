package dev.postmark.model;

/** Fit GUI-transformed model bounds into a capture without clipping, at native pixel density. */
public record StampCaptureFrame(double centerX,double centerY,double units,int pixels) {
    public static StampCaptureFrame around(double minX,double minY,double maxX,double maxY,int basePixels) {
        if(basePixels<4 || !Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(maxX) || !Double.isFinite(maxY)
                || maxX<minX || maxY<minY)throw new IllegalArgumentException("Invalid item capture bounds");
        if(minX>=-.5001 && minY>=-.5001 && maxX<=.5001 && maxY<=.5001)
            return new StampCaptureFrame(0,0,1,basePixels);
        double extent=Math.max(1,Math.max(maxX-minX,maxY-minY));
        int pixels=(int)Math.min(2048,Math.ceil(extent*basePixels)+2);
        // Leave one transparent pixel on each edge, even for large models at the resolution cap.
        return new StampCaptureFrame((minX+maxX)/2,(minY+maxY)/2,extent*pixels/(pixels-2.0),pixels);
    }
}
