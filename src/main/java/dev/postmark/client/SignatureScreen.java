package dev.postmark.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.postmark.model.*;
import dev.postmark.render.PostcardPainter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.awt.image.BufferedImage;
import java.util.*;

/** The postcard's reverse is a persistent freehand drawing surface. */
public final class SignatureScreen extends Screen {
    private static final int INK=0xFF364F62;
    private final PostcardScreen parent;
    private final ClientSession session;
    private final List<InkPoint> current=new ArrayList<>();
    private Identifier front,back;
    private double x,y,w,h;
    private long opened;
    private int serial;
    private String message="",detail="";
    private long messageAt;
    private final List<Action> actions=new ArrayList<>();
    private record Action(int x,int y,int w,String label,Runnable run) {
        boolean hit(double mx,double my) { return mx>=x && mx<x+w && my>=y && my<y+22; }
    }
    public SignatureScreen(PostcardScreen parent,ClientSession session) {
        super(Component.literal("信封背面")); this.parent=parent; this.session=session;
    }
    private Postcard card() { return session.album().selected(); }
    private static long now() { return System.nanoTime()/1_000_000; }
    private void layout() {
        double maxW=Math.max(70,width-220),maxH=Math.max(45,height-100);
        w=Math.min(maxW,maxH*card().aspectRatio())*.8; h=w/card().aspectRatio();
        x=(width-w)/2.0+12; y=(height-h)/2.0;
    }
    @Override protected void init() {
        layout(); if(opened==0) opened=now();
        try {
            if(front==null) front=upload(PostcardPainter.paintEnvelopeFront(card()));
            if(back==null) back=upload(PostcardPainter.paintBack(card().withSignature(List.of())));
        } catch(Exception e) { error(e); }
    }
    private Identifier upload(BufferedImage image) {
        NativeImage pixels=new NativeImage(image.getWidth(),image.getHeight(),false);
        for(int py=0;py<image.getHeight();py++) for(int px=0;px<image.getWidth();px++) pixels.setPixel(px,py,image.getRGB(px,py));
        var id=Identifier.fromNamespaceAndPath("postmark","signature/"+System.identityHashCode(this)+"/"+serial++);
        minecraft.getTextureManager().register(id,new DynamicTexture(()->"Postcard reverse",pixels)); return id;
    }
    private boolean flipping() { return now()-opened<750; }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        if(minecraft.level==null) extractPanorama(g,partialTick);
        extractBlurredBackground(g);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial) {
        layout();
        g.fill(0,0,width,height,0x58303D35);
        actions.clear();
        double t=Math.clamp((now()-opened)/750.0,0,1), smooth=t*t*(3-2*t);
        double scale=Math.max(.006,Math.abs(Math.cos(Math.PI*smooth)));
        double initialScale=EnvelopeLayout.packedWidth(card().aspectRatio(),width,height)/w;
        double grow=initialScale+(1-initialScale)*smooth;
        int dw=(int)(w*scale*grow),dh=(int)(h*grow);
        int left=(int)((width/2.0)*(1-smooth)+(x+w/2)*smooth-dw/2.0),top=(int)((height/2.0+25)*(1-smooth)+(y+h/2)*smooth-dh/2.0);

        g.fill(left+4,top+5,left+dw+5,top+dh+6,0x220F271C);
        Identifier side=t<.5?front:back;
        if(side!=null) g.blit(side,left,top,left+dw,top+dh,0f,1f,0f,1f);
        if(t>=.5) {
            g.enableScissor(left,top,left+dw,top+dh);
            for(var stroke:card().signature()) drawStroke(g,stroke.points(),stroke.color(),stroke.width(),left,top,dw,dh);
            drawStroke(g,current,INK,.0022,left,top,dw,dh);
            g.disableScissor();
        }
        if(!flipping() && inside(mx,my)) {
            // Small nib points to the exact ink position without obscuring the name.
            g.fill(mx+3,my-9,mx+6,my-1,0xFF394F58);
            g.fill(mx+4,my-9,mx+5,my-3,0xFFA2B6B6);
            g.fill(mx+1,my-2,mx+4,my+1,0xFFB69E69);
        }
        if(!flipping()) {
            int sx=(int)(x+w)+22,sy=(int)(y+h)-14;
            seal(g,sx,sy,mx,my);
            int bx=(int)x-22,by=(int)(y+h)-10;
            g.fill(bx-10,by-13,bx+8,by+8,0xFFCCBD9D); g.fill(bx-8,by-15,bx+10,by+5,0xFFF0E8D2);
            g.text(font,"<",bx-3,by-9,0xFF79694E,false);
            actions.add(new Action(bx-14,by-16,30,"return",this::onClose));
            int ux=(int)x-32,uy=(int)(y+h)-86,cy=(int)(y+h)-48;
            boolean hasInk=!current.isEmpty() || !card().signature().isEmpty();
            DeskControls.draw(g,DeskControls.Kind.UNDO,ux,uy,hasInk);
            DeskControls.draw(g,DeskControls.Kind.CLEAR,ux,cy,hasInk);
            actions.add(new Action(ux-13,uy-11,26,"undo",this::undo));
            actions.add(new Action(ux-13,cy-11,26,"clear",()->{if(replace(List.of())) current.clear();}));
            String hint=inside(mx,my)?"按住左键签名；每笔松开后自动保存":"";
            for(var action:actions) if(action.hit(mx,my)) hint=switch(action.label) {
                case "send" -> "保存并寄出 · 签名可留白";
                case "return" -> "返回明信片，保留签名 · Esc";
                case "undo" -> "撤回上一笔 · Ctrl+Z";
                case "clear" -> "清空签名 · Delete";
                default -> "";
            };
            if(my>=height-26 && Math.abs(mx-width/2)<120 && !detail.isEmpty()) hint=detail;
            HoverHint.draw(g,font,hint.isEmpty() && now()-messageAt<3500?message:hint,width,height);
        }
    }
    private static void drawStroke(GuiGraphicsExtractor g,List<InkPoint> points,int color,double weight,double left,double top,double cw,double ch) {
        if(points.isEmpty()) return;
        int thickness=Math.max(1,(int)Math.round(weight*cw));
        InkPoint last=points.getFirst();
        for(var point:points) {
            double ax=left+last.x()*cw,ay=top+last.y()*ch,bx=left+point.x()*cw,by=top+point.y()*ch;
            int steps=Math.max(1,(int)Math.ceil(Math.max(Math.abs(bx-ax),Math.abs(by-ay))));
            for(int i=0;i<=steps;i++) {
                double t=(double)i/steps; int px=(int)Math.round(ax+(bx-ax)*t),py=(int)Math.round(ay+(by-ay)*t);
                g.fill(px-thickness/2,py-thickness/2,px-thickness/2+thickness,py-thickness/2+thickness,color);
            }
            last=point;
        }
    }
    private void seal(GuiGraphicsExtractor g,int cx,int cy,int mx,int my) {
        g.fill(cx-13,cy-8,cx+14,cy+10,0xFF753D31); g.fill(cx-9,cy-13,cx+10,cy+14,0xFF753D31);
        g.fill(cx-11,cy-7,cx+11,cy+8,0xFFAD6147); g.fill(cx-7,cy-11,cx+7,cy+12,0xFFAD6147);
        g.fill(cx-6,cy-9,cx+4,cy-7,0xFFD0946A);
        g.text(font,"P",cx-3,cy-4,0xFFF4D4A0,false);
        actions.add(new Action(cx-19,cy-11,38,"send",this::send));
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if(e.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_Z && (e.modifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL)!=0) { undo(); return true; }
        if(e.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) { if(replace(List.of())) current.clear(); return true; }
        return super.keyPressed(e);
    }
    private boolean inside(double px,double py) { return px>=x && px<=x+w && py>=y && py<=y+h; }
    private void append(double px,double py) {
        if(!inside(px,py)) { finish(); return; }
        if(current.size()>=InkStroke.MAX_POINTS || card().signature().stream().mapToInt(s->s.points().size()).sum()+current.size()>=Postcard.MAX_SIGNATURE_POINTS) { finish(); return; }
        var point=new InkPoint((px-x)/w,(py-y)/h);
        if(current.isEmpty() || Math.hypot((point.x()-current.getLast().x())*w,(point.y()-current.getLast().y())*h)>=.5) current.add(point);
    }
    private boolean finish() {
        if(current.isEmpty()) return true;
        var strokes=new ArrayList<>(card().signature()); strokes.add(new InkStroke(current,INK,.0022));
        if(replace(strokes)) { current.clear(); return true; }
        return false;
    }
    private boolean replace(List<InkStroke> strokes) {
        try { session.update(session.album().replace(card().withSignature(strokes))); message="已保存";detail="";messageAt=now(); return true; }
        catch(Exception e) { error(e); return false; }
    }
    private void error(Exception e) { message="保存失败";detail="保存失败，签名保留："+e.getMessage();messageAt=now(); dev.postmark.Postmark.LOGGER.warn("Signature operation failed",e); }
    private void undo() {
        if(!current.isEmpty()) { current.clear(); return; }
        var strokes=card().signature(); if(!strokes.isEmpty()) replace(strokes.subList(0,strokes.size()-1));
    }
    private void send() { if(!finish()) return; minecraft.setScreen(parent); parent.sendSigned(); }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice) {
        if(flipping() || e.button()!=0) return true;
        for(var a:actions) if(a.hit(e.x(),e.y())) { if(!finish()) return true; a.run.run(); return true; }
        if(inside(e.x(),e.y())) {
            if(!finish()) return true;
            if(card().signature().size()>=Postcard.MAX_STROKES) { message="笔画已满";detail="笔画已满；点击撤回或清空后继续";messageAt=now(); return true; }
            append(e.x(),e.y()); return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy) {
        if(e.button()==0 && !current.isEmpty()) { append(e.x(),e.y()); return true; } return false;
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) {
        if(e.button()==0 && !current.isEmpty()) { append(e.x(),e.y()); finish(); return true; } return false;
    }
    @Override public void onClose() { if(!finish()) return; minecraft.setScreen(parent); }
    @Override public void removed() {
        finish();
        if(front!=null) { minecraft.getTextureManager().release(front); front=null; }
        if(back!=null) { minecraft.getTextureManager().release(back); back=null; }
    }
    @Override public boolean isPauseScreen() { return false; }
}
