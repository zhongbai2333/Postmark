package dev.postmark.model;

/** Smooth screen-space camera. Zoom and translation interpolate together around the cursor. */
public final class GuideViewport {
    public record Bookmark(double centerX,double centerY,double zoom) {
        public Bookmark {if(!Double.isFinite(centerX)||!Double.isFinite(centerY)||!Double.isFinite(zoom)||zoom<=0)throw new IllegalArgumentException("Invalid guide view");}
    }
    private double x,y,zoom=1,targetX,targetY,targetZoom=1,w,h,paperW,paperH;
    public double x(){return x;} public double y(){return y;} public double zoom(){return zoom;}
    public double worldX(double px){return (px-x)/zoom;} public double worldY(double py){return (py-y)/zoom;}
    public void resize(double w,double h,double paperW,double paperH,boolean reset) {
        boolean changed=this.w!=w || this.h!=h || this.paperW!=paperW || this.paperH!=paperH;
        var saved=this.paperW>0&&this.paperH>0?bookmark():null;
        this.w=w;this.h=h;this.paperW=paperW;this.paperH=paperH;
        if(reset)fit(true);else if(changed){if(saved==null)fit(true);else restore(saved);}
    }
    public Bookmark bookmark() {return new Bookmark((w/2-targetX)/targetZoom/paperW,(h/2-targetY)/targetZoom/paperH,targetZoom);}
    public void restore(Bookmark saved) {
        targetZoom=Math.clamp(saved.zoom,Math.max(.015,fitZoom()*.7),2.4);
        targetX=w/2-saved.centerX*paperW*targetZoom;targetY=h/2-saved.centerY*paperH*targetZoom;boundTarget();
        x=targetX;y=targetY;zoom=targetZoom;
    }
    public double fitZoom(){return Math.min(1,Math.min((w-16)/paperW,(h-16)/paperH));}
    public void fit(boolean immediate) {
        targetZoom=fitZoom();targetX=(w-paperW*targetZoom)/2;targetY=(h-paperH*targetZoom)/2;
        if(immediate){zoom=targetZoom;x=targetX;y=targetY;}
    }
    public void readable() {targetZoom=Math.max(fitZoom(),.82);targetX=(w-paperW*targetZoom)/2;targetY=(h-paperH*targetZoom)/2;zoom=targetZoom;x=targetX;y=targetY;}
    public void enter(){zoom=targetZoom*.92;x=(w-paperW*zoom)/2;y=(h-paperH*zoom)/2;}
    public void zoomAt(double px,double py,double factor) {
        double wx=worldX(px),wy=worldY(py);
        targetZoom=Math.clamp(targetZoom*factor,Math.max(.015,fitZoom()*.7),2.4);
        targetX=px-wx*targetZoom;targetY=py-wy*targetZoom;boundTarget();
    }
    public void pan(double dx,double dy) {targetX=x+dx;targetY=y+dy;targetZoom=zoom;boundTarget();x=targetX;y=targetY;}
    private void boundTarget() {
        double margin=45;
        targetX=Math.clamp(targetX,margin-paperW*targetZoom,w-margin);
        targetY=Math.clamp(targetY,margin-paperH*targetZoom,h-margin);
    }
    public void step(double seconds) {
        double a=1-Math.exp(-Math.clamp(seconds,0,.1)/.065);
        x+=(targetX-x)*a;y+=(targetY-y)*a;zoom+=(targetZoom-zoom)*a;
    }
}
