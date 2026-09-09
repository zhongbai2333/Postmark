package dev.postmark.client;

/** Continuous pixel scrolling with a frame-rate-independent, non-overshooting ease. */
public final class SmoothShelfScroll {
    private double position,target,maximum;
    private long lastTime=-1;
    public double position() { return position; }
    public double maximum() { return maximum; }
    public void bounds(double maximum) {
        this.maximum=Math.max(0,maximum);
        target=Math.clamp(target,0,this.maximum);position=Math.clamp(position,0,this.maximum);
    }
    public void scroll(double wheelDelta) {
        if(Double.isFinite(wheelDelta)) target=Math.clamp(target-wheelDelta*48,0,maximum);
    }
    /** Reveal a slot by moving the scroll target, without jumping the rendered position. */
    public void reveal(double center,double visibleTop,double visibleBottom) {
        if(!Double.isFinite(center) || visibleBottom<visibleTop) return;
        if(center-target<visibleTop || center-target>visibleBottom)
            target=Math.clamp(center-(visibleTop+visibleBottom)/2,0,maximum);
    }
    public long settlingMillis() {
        double distance=Math.abs(target-position);
        return distance<=.5?0:(long)Math.ceil(75*Math.log(distance/.5));
    }
    public void advance(long now) {
        if(lastTime<0) { lastTime=now;return; }
        double elapsed=Math.max(0,now-lastTime);lastTime=now;
        position+=(target-position)*(-Math.expm1(-elapsed/75.0));
        if(Math.abs(target-position)<.02) position=target;
    }
}
