package dev.postmark.client;

import dev.postmark.model.StampDefinition;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.List;

/** A whole saved batch fans out, pauses, then flows into the bag together. */
public final class StampInboxAnimation {
    public static final long DURATION=3000;
    private final List<StampDefinition> stamps;
    private final long started;
    public StampInboxAnimation(List<StampDefinition> stamps,long now){this.stamps=List.copyOf(stamps);started=now;}
    public List<StampDefinition> stamps(){return stamps;}
    public boolean finished(long now){return now-started>=DURATION;}
    private static double ease(double t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
    public void render(GuiGraphicsExtractor g,Font font,StampAcquisitionAnimation.ToolPainter painter,int width,int height,double tx,double ty,long now) {
        double age=now-started;int count=stamps.size();if(count==0||age>=DURATION)return;
        int cols=Math.max(1,(int)Math.ceil(Math.sqrt(count*1.55))),rows=(count+cols-1)/cols;
        double spacing=Math.min(84,Math.min(width*.72/cols,height*.62/rows));
        double centerX=width*.54,centerY=height*.51;
        double spread=ease(age/480),fade=1-ease((age-1450)/400);
        for(int i=0;i<count;i++) {
            int row=i/cols,col=i%cols,rowCount=Math.min(cols,count-row*cols);
            double x=centerX+(col-(rowCount-1)/2.0)*spacing*spread;
            double y=centerY+(row-(rows-1)/2.0)*spacing*spread;
            double fly=ease((age-1650-Math.min(300,i*18))/850);
            x+=(tx-x)*fly;y+=(ty-y)*fly-Math.sin(Math.PI*fly)*height*.14;
            int size=(int)(spacing*.65*(.55+.45*spread)*(1-fly)+24*fly);
            painter.draw(g,stamps.get(i),x,y,(col%2==0?-.055:.055)*(1-fly)*spread,size,1,(float)(1-ease((fly-.85)/.15)));
        }
        if(fade>0){String text=count+" 枚新章入袋";int w=font.width(text)+24,cx=(int)centerX,y=(int)(centerY+rows*spacing/2+18);
            g.fill(cx-w/2,y-4,cx+w/2,y+14,((int)(fade*240)<<24)|0xF1E3BD);
            g.centeredText(font,Component.literal(text),cx,y,((int)(fade*255)<<24)|0x435B42);}
    }
}
