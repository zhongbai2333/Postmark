package dev.postmark.client;

import dev.postmark.model.StampDefinition;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** An open leather pouch: a fixed honeycomb pile, with matching stamps lifted to its mouth. */
public final class StampBag {
    @FunctionalInterface public interface FacePainter { void draw(GuiGraphicsExtractor g,StampDefinition stamp,double x,double y,double size,float opacity); }
    private final Font font;
    private final FacePainter painter;
    private final Consumer<StampDefinition> take;
    public final EditBox search;
    private List<StampDefinition> collection=List.of(),ordered=List.of();
    private final Map<String,StampBagIndex.Point> shown=new HashMap<>(),starts=new HashMap<>();
    private final Map<String,Double> hoverScale=new HashMap<>();
    private boolean open,panning;
    private String query="";
    private long changedAt,lastFrame;
    private double zoom=1,zoomTarget=1,panX,panY,pressX,pressY;
    private int x,y,w,h,columns=1;
    private boolean fitted;
    public StampBag(Font font,FacePainter painter,Consumer<StampDefinition> take) {
        this.font=font;this.painter=painter;this.take=take;
        search=new EditBox(font,0,0,200,18,Component.literal("搜索袋里的章"));
        search.setMaxLength(100);search.setBordered(false);search.setTextColor(0xFFF1E5CA);
        search.setTextShadow(false);search.setHint(Component.literal("输入展区名称，把章找出来…"));
        search.setResponder(value->{starts.clear();starts.putAll(shown);query=value;panX=panY=0;changedAt=now();});
        search.setVisible(false);
    }
    private static long now() { return System.nanoTime()/1_000_000; }
    public boolean isOpen() { return open; }
    public void layout(int width,int height) {
        int nextW=Math.max(180,Math.min(560,width-48)),nextH=Math.max(170,Math.min(354,height-48));
        if(nextW!=w || nextH!=h) fitted=false;
        w=nextW;h=nextH;x=(width-w)/2;y=(height-h)/2;
        search.setX(x+45);search.setY(y+29);search.setWidth(Math.max(80,w-115));
    }
    public void open(List<StampDefinition> stamps) { sync(stamps);open=true;panning=false;lastFrame=now();search.setVisible(true);search.setFocused(true); }
    public void close() { open=false;panning=false;search.setVisible(false);search.setFocused(false); }
    private void sync(List<StampDefinition> stamps) {
        if(collection.equals(stamps)) return;
        collection=List.copyOf(stamps);ordered=StampBagIndex.ordered(stamps);fitted=false;
        shown.clear();starts.clear();hoverScale.clear();
    }
    private double gridWidth() { return (columns+.5)*42; }
    private double gridHeight() { return Math.max(1,(ordered.size()+columns-1)/columns)*36.373; }
    private void fit() {
        if(fitted) return;
        columns=Math.clamp((int)Math.ceil(Math.sqrt(Math.max(1,ordered.size())*(w-70.0)/Math.max(60,h-115)*.866)),2,16);
        zoom=Math.clamp(Math.min((w-80)/gridWidth(),(h-120)/gridHeight()),.65,1.25);
        panX=panY=0;zoomTarget=zoom;fitted=true;
    }
    private void clampPan() {
        double mx=Math.max(0,(gridWidth()*zoom-(w-80))/2)+24;
        double my=Math.max(0,(gridHeight()*zoom-(h-120))/2)+24;
        if(!query.isBlank()) {
            int count=matchCount(),cols=Math.max(1,Math.min(count,Math.max(1,(w-110)/58)));
            int rows=Math.max(1,(count+cols-1)/cols);
            mx=24;my=Math.max(0,(rows*52*.75-(h-130))/2)+24;
        }
        panX=Math.clamp(panX,-mx,mx);panY=Math.clamp(panY,-my,my);
    }
    private StampBagIndex.Point home(int index) {
        var c=StampBagIndex.cell(index,columns);
        return new StampBagIndex.Point(x+w/2.0+(c.x()-gridWidth()/2+21)*zoom+panX,
                y+75+(h-110)/2.0+(c.y()-gridHeight()/2+18)*zoom+panY);
    }
    public StampBagIndex.Point positionOf(String key) { return shown.get(key); }
    public double magnification(String key) { return hoverScale.getOrDefault(key,1.0); }
    public int matchCount() { return (int)ordered.stream().filter(s->StampBagIndex.matches(s,query)).count(); }
    public List<StampDefinition> matches() { return ordered.stream().filter(s->StampBagIndex.matches(s,query)).toList(); }
    private boolean inside(double mx,double my) { return mx>=x+18 && mx<x+w-18 && my>=y+64 && my<y+h-35; }
    private StampDefinition hit(double mx,double my) {
        if(!inside(mx,my) || now()-changedAt<300) return null;
        StampDefinition best=null;double distance=Double.POSITIVE_INFINITY;
        boolean filtering=!query.isBlank();
        double radius=filtering?21:21*zoom;
        for(var s:ordered) {
            if(filtering && !StampBagIndex.matches(s,query)) continue;
            var c=shown.get(s.key());if(c==null) continue;
            double d=Math.hypot(mx-c.x(),my-c.y());
            if(d<=radius && d<distance) { best=s;distance=d; }
        }
        return best;
    }
    public void render(GuiGraphicsExtractor g,int mx,int my,float partial,List<StampDefinition> stamps) {
        if(!open) return;
        sync(stamps);fit();clampPan();
        long time=now();double dt=Math.clamp(time-lastFrame,0,100);lastFrame=time;
        zoom+=(zoomTarget-zoom)*(1-Math.exp(-dt/75));clampPan();
        g.fill(0,0,x*2+w,y*2+h,0x86322D25);
        // Stepped corners, deep lining and folded leather rim.
        g.fill(x+12,y+14,x+w+8,y+h+9,0x48302319);
        g.fill(x+12,y+5,x+w-12,y+h,0xFF604530);g.fill(x+4,y+19,x+w-4,y+h-14,0xFF795337);
        g.fill(x+14,y+15,x+w-14,y+h-15,0xFFA0774E);
        g.fill(x+21,y+57,x+w-21,y+h-34,0xFF49392B);
        g.fill(x+27,y+63,x+w-27,y+h-40,0xFF584532);
        g.fill(x+30,y+65,x+w-30,y+70,0xFF3B3025);
        for(int sy=y+74;sy<y+h-43;sy+=12) {
            g.fill(x+23,sy,x+25,sy+5,0xFFA27D50);g.fill(x+w-25,sy,x+w-23,sy+5,0xFFA27D50);
        }
        g.fill(x+28,y+20,x+w-60,y+46,0xFF654D36);
        g.fill(x+30,y+22,x+w-62,y+24,0xFFB99362);
        if(query.isEmpty() && search.isFocused()) g.text(font,Component.literal("写下展区名，把章找出来…"),search.getX()+8,search.getY(),0xFFAD9676,false);
        g.fill(x+34,y+29,x+40,y+35,0xFFCBB184);g.fill(x+35,y+30,x+39,y+34,0xFF654D36);g.fill(x+39,y+35,x+43,y+38,0xFFCBB184);
        search.extractRenderState(g,mx,my,partial);
        g.text(font,Component.literal("×"),x+w-39,y+29,0xFFF3DFC0,false);
        if(!query.isEmpty()) g.text(font,Component.literal("×"),x+w-77,y+29,0xFFDBC59D,false);
        boolean filtering=!query.isBlank();
        var found=matches();Map<String,Integer> ranks=new HashMap<>();
        for(int i=0;i<found.size();i++) ranks.put(found.get(i).key(),i);
        int resultColumns=Math.max(1,Math.min(found.size(),Math.max(1,(w-110)/58)));
        int rows=Math.max(1,(found.size()+resultColumns-1)/resultColumns);
        double resultScale=Math.min(1,Math.max(.75,(h-130.0)/(rows*52)));
        double t=Math.clamp((time-changedAt)/300.0,0,1);t=t*t*(3-2*t);
        for(int i=0;i<ordered.size();i++) {
            var s=ordered.get(i);var target=home(i);
            if(filtering && ranks.containsKey(s.key())) {
                int rank=ranks.get(s.key()),row=rank/resultColumns,col=rank%resultColumns;
                int rowCount=Math.min(resultColumns,found.size()-row*resultColumns);
                target=new StampBagIndex.Point(x+w/2.0+(col-(rowCount-1)/2.0)*58*resultScale+panX,
                        y+75+(h-110)/2.0+(row-(rows-1)/2.0)*52*resultScale+panY);
            }
            var start=starts.getOrDefault(s.key(),home(i));
            shown.put(s.key(),new StampBagIndex.Point(start.x()+(target.x()-start.x())*t,start.y()+(target.y()-start.y())*t));
        }
        var hovered=panning?null:hit(mx,my);
        g.enableScissor(x+18,y+63,x+w-18,y+h-35);
        for(var s:ordered) {
            boolean match=!filtering || ranks.containsKey(s.key());
            if(filtering && match) continue;
            drawFace(g,s,hovered,dt,30*zoom,match?1f:.13f);
        }
        if(filtering) {
            // The pile stays underneath; matching stamps rise above a soft leather shadow.
            for(var s:found) drawFace(g,s,hovered,dt,38*resultScale,1f);
        }
        if(hovered!=null) {
            var c=shown.get(hovered.key());double size=(filtering?38*resultScale:30*zoom)*hoverScale.getOrDefault(hovered.key(),1.0);
            painter.draw(g,hovered,c.x(),c.y()-3,size,1);
        }
        g.disableScissor();
        // Thick folded leather mouth with pixel stitching.
        g.fill(x+13,y+h-34,x+w-13,y+h-14,0xFF946B45);
        g.fill(x+13,y+h-34,x+w-13,y+h-30,0xFFC49B67);
        for(int sx=x+22;sx<x+w-20;sx+=9) g.fill(sx,y+h-20,sx+4,y+h-18,0xFFE0BB82);
        String note=hovered!=null?hovered.name():filtering?(found.isEmpty()?"没有找到，换个名称试试":"从袋里找出了 "+found.size()+" 枚章"):
                ordered.size()+" 枚章 · 指向放大 · 点击拿起 · 空隙拖动 · 滚轮缩放";
        if(font.width(note)>w-32) note=font.plainSubstrByWidth(note,w-44)+"…";
        g.centeredText(font,Component.literal(note),x+w/2,y+h+15,0xFFF0E2C4);
    }
    private void drawFace(GuiGraphicsExtractor g,StampDefinition s,StampDefinition hovered,double dt,double size,float opacity) {
        double scale=hoverScale.getOrDefault(s.key(),1.0),target=s.equals(hovered)?1.65:1;
        scale+=(target-scale)*(1-Math.exp(-dt/70));hoverScale.put(s.key(),scale);
        if(s.equals(hovered)) return;
        var c=shown.get(s.key());if(c.y()<y+40 || c.y()>y+h || c.x()<x || c.x()>x+w) return;
        painter.draw(g,s,c.x(),c.y(),size*scale,opacity);
    }
    public boolean click(MouseButtonEvent e) {
        if(!open) return false;
        if(e.button()==0) {
            if(e.x()>x+w-50 && e.x()<x+w-20 && e.y()>=y+18 && e.y()<y+50) { close();return true; }
            if(e.x()>x+w-86 && e.x()<x+w-65 && e.y()>=y+18 && e.y()<y+50) { search.setValue("");search.setFocused(true);return true; }
            if(e.y()>=y+20 && e.y()<y+50) { search.setFocused(true);search.mouseClicked(e,false);return true; }
            var s=hit(e.x(),e.y());
            if(s!=null) { close();take.accept(s);return true; }
        }
        if(inside(e.x(),e.y())) { panning=true;pressX=e.x();pressY=e.y();search.setFocused(false); }
        return true;
    }
    public void drag(MouseButtonEvent e) {
        if(panning) { panX+=e.x()-pressX;panY+=e.y()-pressY;pressX=e.x();pressY=e.y();clampPan(); }
    }
    public void release() { panning=false; }
    public void scroll(double delta) {
        if(!query.isBlank()) { panY+=delta*32;clampPan();return; }
        zoomTarget=Math.clamp(zoomTarget*Math.exp(delta*.1),.5,2.5);
    }
}
