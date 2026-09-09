package dev.postmark.client;

import dev.postmark.model.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.IntConsumer;

/** A reversible luggage tag with ten punched holes and small milestone tickets. */
public final class CollectionTag {
    private final Font font;
    private final IntConsumer takeTicket;
    private CollectionProgress progress=CollectionProgress.from(List.of());
    private List<StampDefinition> observed;
    private boolean back;
    private long turnedAt=-1000,updatedAt=-1000;
    private int previous,selected;
    private int x=76,bottom;
    public CollectionTag(Font font,IntConsumer takeTicket) { this.font=font;this.takeTicket=takeTicket; }
    private static long now() { return System.nanoTime()/1_000_000; }
    public boolean sync(List<StampDefinition> stamps) {
        if(stamps.equals(observed)) return false;
        var next=CollectionProgress.from(stamps);
        boolean fresh=observed!=null && next.total()>progress.total();
        previous=progress.total();progress=next;observed=List.copyOf(stamps);
        if(fresh) updatedAt=now();
        if(selected==0 || selected>progress.milestone() || fresh) selected=progress.milestone();
        return fresh;
    }
    public CollectionProgress progress() { return progress; }
    public boolean isOpen() { return back; }
    public void close() { if(back) { back=false;turnedAt=now(); } }
    public void layout(int width,int height) { x=Math.min(76,Math.max(4,width-204));bottom=height-35; }
    public double anchorX() { return x+24; }
    public double anchorY() { return bottom-28; }
    private double openAmount() {
        double t=Math.clamp((now()-turnedAt)/260.0,0,1);t=t*t*(3-2*t);return back?t:1-t;
    }
    public void render(GuiGraphicsExtractor g,int mx,int my) {
        double open=openAmount();int w=(int)(48+148*open),h=(int)(60+68*open),y=bottom-h;
        double pulse=Math.max(0,1-(now()-updatedAt)/650.0);
        g.pose().pushMatrix();g.pose().rotateAbout((float)(Math.sin((now()-updatedAt)/65.0)*.055*pulse*(1-open)),x+24,y+4);
        float flip=(float)Math.max(.025,Math.abs(Math.cos(Math.PI*Math.clamp((now()-turnedAt)/260.0,0,1))));
        g.pose().translate(x+w/2f,0);g.pose().scale(flip,1);g.pose().translate(-(x+w/2f),0);
        g.fill(x+3,y+4,x+w+3,bottom+4,0x4032261B);
        g.fill(x+3,y,x+w-3,bottom,0xFFE5D4AF);g.fill(x,y+5,x+w,bottom-4,0xFFE5D4AF);
        g.fill(x+3,y+2,x+w-3,y+4,0xFFF8EACD);g.fill(x+3,bottom-3,x+w-3,bottom,0xFFBBA17B);
        // A compact eyelet connects the tag to the bag; no protruding decorative cords.
        g.fill(x+18,y-2,x+29,y+8,0xFF9C7B48);g.fill(x+21,y,x+26,y+5,0xFF594A35);
        if(open<.5) {
            g.fill(x+20,y+15,x+28,y+19,0xFF6D8063);g.fill(x+22,y+19,x+26,y+23,0xFF6D8063);g.fill(x+17,y+23,x+31,y+27,0xFF6D8063);
            double t=Math.clamp((now()-updatedAt)/230.0,0,1);
            int count=(int)Math.round(previous+(progress.total()-previous)*t);
            number(g,Integer.toString(count),x+24,y+34,1.5f,0xFF4B5D45);
            for(int i=0;i<progress.holes();i++) g.fill(x+9+i*3,bottom-9,x+10+i*3,bottom-7,0xFF947C57);
        } else if(open>.95) {
            String kinds="普通 "+progress.visitors()+" · 大师 "+progress.experts();
            if(progress.others()>0) kinds+=" · 其他 "+progress.others();
            g.text(font,Component.literal(kinds),x+12,y+16,0xFF4D5140,false);
            g.text(font,Component.literal("展馆 "+progress.venues()),x+12,y+30,0xFF736E55,false);
            for(int i=0;i<10;i++) {
                int hx=x+17+(i%5)*15,hy=y+49+(i/5)*14;
                g.fill(hx-1,hy-1,hx+7,hy+7,0xFFC4AD83);
                g.fill(hx,hy,hx+6,hy+6,i<progress.holes()?0xFF5B674D:0xFFE5D4AF);
                if(i<progress.holes()) g.fill(hx+1,hy+1,hx+5,hy+2,0xFF3F4B38);
            }
            g.centeredText(font,Component.literal("目标"),x+140,y+38,0xFF8A7A58);
            number(g,Integer.toString(progress.target()),x+140,y+49,1.7f,0xFF59704E);
            g.centeredText(font,Component.literal(progress.holes()+" / 10"),x+140,y+69,0xFF8A7A58);
            if(selected>0) {
                int ty=y+91;g.fill(x+46,ty+2,x+152,ty+26,0xFFB9A280);
                g.fill(x+44,ty,x+150,ty+24,0xFFF8EDD3);
                for(int dy=2;dy<23;dy+=5) { g.fill(x+44,ty+dy,x+47,ty+dy+2,0xFFE5D4AF);g.fill(x+147,ty+dy,x+150,ty+dy+2,0xFFE5D4AF); }
                g.fill(x+56,ty+7,x+64,ty+15,0xFF7F936B);
                g.centeredText(font,Component.literal(Integer.toString(selected)),x+98,ty+8,0xFF4B5D45);
                g.fill(x+126,ty+7,x+138,ty+9,0xFFC6B489);g.fill(x+126,ty+12,x+135,ty+14,0xFFC6B489);
                if(selected>10) g.text(font,Component.literal("‹"),x+22,ty+8,0xFF806F50,false);
                if(selected<progress.milestone()) g.text(font,Component.literal("›"),x+168,ty+8,0xFF806F50,false);
            }
        }
        g.pose().popMatrix();
    }
    private void number(GuiGraphicsExtractor g,String value,int cx,int y,float scale,int color) {
        g.pose().pushMatrix();g.pose().translate(cx,y);g.pose().scale(scale,scale);
        g.text(font,Component.literal(value),-font.width(value)/2,0,color,false);g.pose().popMatrix();
    }
    public String hint(double mx,double my) {
        double open=openAmount();int w=(int)(48+148*open),h=(int)(60+68*open),y=bottom-h;
        if(mx<x || mx>x+w || my<y || my>bottom) return "";
        if(!back) return "集章牌 · 点击翻面；只统计不同收藏，不计重复盖印";
        if(my>=y+88 && selected>0) return "纪念票 · 两侧切换，点击拿起后放到明信片上";
        if(my>=y+45) return "每十枚打满一张孔卡，解锁一张纪念票";
        return "已收集的普通章、大师章与展馆；点击收起";
    }
    public boolean click(MouseButtonEvent e) {
        if(e.button()!=0) return false;
        double open=openAmount();int w=(int)(48+148*open),h=(int)(60+68*open),y=bottom-h;
        if(e.x()<x || e.x()>x+w || e.y()<y || e.y()>bottom) return false;
        if(now()-turnedAt<260) return true;
        if(back && selected>0 && e.y()>=y+88 && e.y()<y+119) {
            if(e.x()<x+40) { selected=Math.max(10,selected-10);return true; }
            if(e.x()>x+158) { selected=Math.min(progress.milestone(),selected+10);return true; }
            if(progress.unlocks(selected)) { takeTicket.accept(selected);close();return true; }
        }
        back=!back;turnedAt=now();return true;
    }
}
