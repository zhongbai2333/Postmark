package dev.postmark.client;

import dev.postmark.model.StampDefinition;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Brief pixel-art stamp reveal; entirely visual, never intercepts input or changes the postcard. */
public final class StampAcquisitionAnimation {
    public static final long DURATION=2400;
    @FunctionalInterface public interface ToolPainter {
        void draw(GuiGraphicsExtractor g,StampDefinition stamp,double x,double y,double angle,int size,double press,float opacity);
    }
    private final StampDefinition stamp;
    private final long started;
    private int soundStage;
    public StampAcquisitionAnimation(StampDefinition stamp,long started) {this.stamp=stamp;this.started=started;}
    public StampDefinition stamp() {return stamp;}
    public long elapsed(long now) {return Math.max(0,now-started);}
    public boolean finished(long now) {return elapsed(now)>=DURATION;}
    /** Play each cue once even if a slow frame skips the impact. */
    public int sound(long now) {
        int stage=elapsed(now)>=1750?2:elapsed(now)>=280?1:0;
        if(stage<=soundStage)return 0;soundStage=stage;return stage;
    }
    private static double ease(double t) {t=Math.clamp(t,0,1);return t*t*(3-2*t);}
    private static int color(double alpha,int rgb) {return (Math.clamp((int)(alpha*255),0,255)<<24)|rgb;}
    public void render(GuiGraphicsExtractor g,Font font,ToolPainter painter,int width,int height,double targetX,double targetY,long now) {
        long age=elapsed(now);if(age>=DURATION)return;
        double unit=Math.min(1,Math.min(width/480.0,height/300.0));
        double cx=width*.54,cy=height*.55;
        double enter=ease(age/280.0),impact=Math.clamp((age-280)/520.0,0,1);
        double leave=ease((age-1750)/650.0),alpha=1-ease((age-1650)/320.0);
        double bounce=age<280?0:Math.sin(Math.min(1,(age-280)/280.0)*Math.PI)*8*unit;
        // A small paper seal underneath, with stepped edges and a crisp pixel ring.
        if(alpha>0 && age>=230) {
            g.pose().pushMatrix();g.pose().translate((float)cx,(float)(cy+5*unit));g.pose().scale((float)unit,(float)unit);
            int r=57;
            g.fill(-r+8,-r,r-8,r,color(.88*alpha,0xEFE2BC));
            g.fill(-r,-r+8,r,r-8,color(.88*alpha,0xEFE2BC));
            for(int i=0;i<48;i++) {
                double a=i*Math.PI/24;int radius=(int)(54+impact*23);
                int x=(int)Math.round(Math.cos(a)*radius),y=(int)Math.round(Math.sin(a)*radius);
                g.fill(x-1,y-1,x+2,y+2,color(alpha*(1-impact)*.85,stamp.expert()?0xB18937:0x537D61));
            }
            for(int i=0;i<12;i++) {
                double a=i*Math.PI/6+.15;double radius=53+impact*35;
                int x=(int)(Math.cos(a)*radius),y=(int)(Math.sin(a)*radius);
                int c=color(alpha*(1-impact*.8),i%2==0?0xCBA650:0x668361);
                g.fill(x-1,y-3,x+2,y+4,c);g.fill(x-3,y-1,x+4,y+2,c);
            }
            // Narrow torn-paper caption leaves the actual artwork as the focus.
            g.fill(-53,63,55,79,color(alpha*.24,0x4C3B24));
            g.fill(-55,61,53,77,color(alpha,0xF4E8CC));
            g.fill(-55,61,-52,64,color(alpha,0xBEA77D));
            g.centeredText(font,Component.literal(stamp.expert()?"大师章入藏":"新章入藏"),0,65,color(alpha,0x435B42));
            g.pose().popMatrix();
        }
        double x=cx+(targetX-cx)*leave;
        double y=cy-65*unit*(1-enter)-bounce+(targetY-cy)*leave-Math.sin(leave*Math.PI)*45*unit;
        double size=(86*unit*(.8+.2*enter))*(1-leave)+26*leave;
        painter.draw(g,stamp,x,y,(-.17*(1-enter)+.2*Math.sin(leave*Math.PI)),(int)size,1,(float)(1-ease((age-2260)/140.0)));
    }
}
