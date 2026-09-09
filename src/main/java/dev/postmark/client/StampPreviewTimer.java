package dev.postmark.client;

/** Monotonic-time idle detection shared by drag-to-place and click-to-hold. */
public final class StampPreviewTimer {
    private long stationarySince;
    private double x,y,angle;
    private boolean initialized,revealed;
    public void reset(double x,double y,double angle,long now) {
        this.x=x;this.y=y;this.angle=angle;stationarySince=now;initialized=true;revealed=false;
    }
    public void update(double x,double y,double angle,long now) {
        if(initialized && now-stationarySince>=500) revealed=true;
        if(!revealed && (!initialized || this.x!=x || this.y!=y || this.angle!=angle)) reset(x,y,angle,now);
    }
    public float amount(long now) {
        if(initialized && now-stationarySince>=500) revealed=true;
        return initialized?Math.clamp((now-stationarySince-500)/180f,0f,1f):0f;
    }
}
