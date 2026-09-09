package dev.postmark.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.postmark.model.Postcard;
import dev.postmark.render.PostcardPainter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.*;

/** An eight-card spread: two leaves with four proportional photographs on each. */
public final class PostcardAlbumScreen extends Screen {
    private final PostcardScreen parent;
    private final ClientSession session;
    private final Map<UUID,Identifier> thumbnails=new HashMap<>();
    private int spread,previous,direction,serial;
    private long turnAt;
    private UUID deletion;
    private String error="";
    private int bx,by,bw,bh,lw;
    private record Slot(int x,int y,int w,int h,int index) {
        boolean hit(double px,double py) {return px>=x && px<x+w && py>=y && py<y+h;}
        int deleteX() {return x+w-14;} int deleteY() {return y+h-13;}
    }
    public PostcardAlbumScreen(PostcardScreen parent,ClientSession session) {
        super(Component.literal("收集册"));this.parent=parent;this.session=session;
        spread=session.album().cards().indexOf(session.album().selected())/8;
    }
    private static long now() {return System.nanoTime()/1_000_000;}
    public int spreadIndex() {return spread;}
    public int spreadCount() {return (session.album().cards().size()+7)/8;}
    public UUID pendingDeletion() {return deletion;}
    private boolean turning() {return turnAt!=0;}
    private void layout() {bw=Math.min(600,width-64);bh=Math.min(346,height-82);lw=(bw-12)/2;bx=(width-bw)/2;by=(height-bh)/2-4;}
    @Override protected void init() {layout();prepare();}
    private List<Slot> slots(int page) {
        var result=new ArrayList<Slot>();int tw=(lw-30)/2,th=(bh-40)/2;
        for(int i=0;i<8;i++) {
            int leaf=i/4,local=i%4,index=page*8+i;if(index>=session.album().cards().size()) break;
            result.add(new Slot(bx+leaf*(lw+12)+10+(local%2)*(tw+10),by+14+(local/2)*(th+8),tw,th,index));
        }
        return result;
    }
    private void prepare() {
        var keep=new HashSet<UUID>();
        for(int page:turning()?new int[]{spread,previous}:new int[]{spread}) for(var slot:slots(page)) {
            var card=session.album().cards().get(slot.index);keep.add(card.id());
            if(thumbnails.containsKey(card.id())) continue;
            try {
                var original=PostcardPainter.paint(card,session.store()::image);
                double scale=360.0/Math.max(original.getWidth(),original.getHeight());
                int w=Math.max(1,(int)Math.round(original.getWidth()*scale)),h=Math.max(1,(int)Math.round(original.getHeight()*scale));
                var thumb=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g=thumb.createGraphics();PostcardPainter.pixel(g);g.drawImage(original,0,0,w,h,null);g.dispose();
                var pixels=new NativeImage(w,h,false);
                for(int y=0;y<h;y++) for(int x=0;x<w;x++) pixels.setPixel(x,y,thumb.getRGB(x,y));
                var id=Identifier.fromNamespaceAndPath("postmark","album/"+System.identityHashCode(this)+"/"+serial++);
                minecraft.getTextureManager().register(id,new DynamicTexture(()->"Postcard album thumbnail",pixels));thumbnails.put(card.id(),id);
            } catch(Exception e) {error="缩略图读取失败："+e.getMessage();}
        }
        var iterator=thumbnails.entrySet().iterator();
        while(iterator.hasNext()) {var entry=iterator.next();if(!keep.contains(entry.getKey())) {minecraft.getTextureManager().release(entry.getValue());iterator.remove();}}
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float t) {
        if(minecraft.level==null) extractPanorama(g,t);extractBlurredBackground(g);
    }
    private void leaf(GuiGraphicsExtractor g,int page,int side) {
        int left=bx+side*(lw+12);
        g.fill(left,by,left+lw,by+bh,0xFFECE2C9);g.fill(left+2,by+2,left+lw-2,by+4,0xFFFFF4D9);
        g.fill(left+2,by+bh-3,left+lw-2,by+bh,0xFFBEAC88);
        for(var slot:slots(page)) if((slot.index%8)/4==side) drawSlot(g,slot);
    }
    private void drawSlot(GuiGraphicsExtractor g,Slot slot) {
        var card=session.album().cards().get(slot.index);int cx=slot.x+slot.w/2,areaH=slot.h-27;
        int w=Math.max(1,(int)Math.min(slot.w-16,areaH*card.aspectRatio())),h=Math.max(1,(int)(w/card.aspectRatio()));
        int x=cx-w/2,y=slot.y+(areaH-h)/2;
        g.fill(x+2,y+3,x+w+2,y+h+3,0x44382D20);
        g.fill(x-2,y-2,x+w+2,y+h+2,card.id().equals(session.album().current())?0xFF8B9D78:0xFFF8EFDA);
        var texture=thumbnails.get(card.id());if(texture!=null) g.blit(texture,x,y,x+w,y+h,0f,1f,0f,1f);
        else g.centeredText(font,Component.literal("未读取"),cx,y+Math.max(0,h/2-4),0xFF866E52);
        g.text(font,Component.literal(Integer.toString(slot.index+1)),slot.x+7,slot.y+slot.h-17,0xFF8F7D5D,false);
        DeskControls.draw(g,DeskControls.Kind.DELETE,slot.deleteX(),slot.deleteY(),true);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial) {
        layout();g.fill(0,0,width,height,0x68303D35);
        g.fill(bx-9,by-6,bx+bw+10,by+bh+10,0x34312720);g.fill(bx-6,by-5,bx+bw+6,by+bh+5,0xFF79573C);
        g.fill(bx-3,by-3,bx+bw+3,by+bh+3,0xFFA27B50);
        if(!turning()) {leaf(g,spread,0);leaf(g,spread,1);}
        else {
            double t=Math.clamp((now()-turnAt)/460.0,0,1),e=t*t*(3-2*t);float fold=(float)Math.max(.002,Math.abs(Math.cos(Math.PI*e)));
            leaf(g,direction>0?previous:spread,0);leaf(g,direction>0?spread:previous,1);
            int side=e<.5?(direction>0?1:0):(direction>0?0:1),page=e<.5?previous:spread;
            float hinge=bx+lw+(side==1?12:0);
            g.pose().pushMatrix();g.pose().translate(hinge,0);g.pose().scale(fold,1);g.pose().translate(-hinge,0);leaf(g,page,side);g.pose().popMatrix();
        }
        g.fill(bx+lw,by,bx+lw+12,by+bh,0xFFB7A27D);g.fill(bx+lw+5,by+2,bx+lw+7,by+bh-2,0xFF8D7856);
        for(int y=by+15;y<by+bh-10;y+=18) g.fill(bx+lw+5,y,bx+lw+7,y+5,0xFFDBC9A5);
        g.centeredText(font,Component.literal("收集册"),width/2,by-20,0xFFE8DBC0);
        arrow(g,-1,bx+20,by+bh+18,spread>0);arrow(g,1,bx+bw-20,by+bh+18,spread+1<spreadCount());
        g.centeredText(font,Component.literal((spread+1)+" / "+spreadCount()),width/2,by+bh+14,0xFFE8DBC0);
        DeskControls.draw(g,DeskControls.Kind.CLOSE,width-22,22,true);
        String hint="";
        if(!turning()) for(var slot:slots(spread)) if(slot.hit(mx,my)) hint=DeskControls.hit(mx,my,slot.deleteX(),slot.deleteY())?"从收集册删除这张明信片；已导出的图片保留":"点击取出这张明信片";
        if(DeskControls.hit(mx,my,bx+20,by+bh+18)) hint="向前翻页";
        if(DeskControls.hit(mx,my,bx+bw-20,by+bh+18)) hint="向后翻页";
        if(DeskControls.hit(mx,my,width-22,22)) hint="合上收集册 · Esc";
        if(deletion!=null) {
            g.fill(0,0,width,height,0x90322D25);int cx=width/2,cy=height/2;
            g.fill(cx-82,cy-42,cx+85,cy+49,0x55432F20);g.fill(cx-85,cy-45,cx+82,cy+45,0xFFF0E3C5);
            g.centeredText(font,Component.literal("删除这张？"),cx,cy-26,0xFF705A43);
            DeskControls.draw(g,DeskControls.Kind.CONFIRM,cx-30,cy+17,true);DeskControls.draw(g,DeskControls.Kind.CLOSE,cx+30,cy+17,true);
            hint=DeskControls.hit(mx,my,cx-30,cy+17)?"确认删除这张草稿；已导出的本地图片保留":DeskControls.hit(mx,my,cx+30,cy+17)?"取消删除":"";
        }
        if(!error.isEmpty() && my>=height-27) hint=error;
        HoverHint.draw(g,font,hint.isEmpty() && !error.isEmpty()?"未完成":hint,width,height);
    }
    private static void arrow(GuiGraphicsExtractor g,int direction,int x,int y,boolean enabled) {
        for(int row=-3;row<=3;row++) {int dx=(Math.abs(row)-1)*2*-direction;g.fill(x+dx-1,y+row*2-1,x+dx+2,y+row*2+2,enabled?0xDDFFF0CD:0x447E735D);}
    }
    private void turn(int step) {int next=Math.clamp(spread+step,0,spreadCount()-1);if(next==spread || turning()) return;previous=spread;spread=next;direction=step;turnAt=now();prepare();}
    @Override public void tick() {if(turning() && now()-turnAt>=460) {turnAt=0;prepare();}}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice) {
        if(e.button()!=0 || turning()) return true;
        if(deletion!=null) {
            if(DeskControls.hit(e.x(),e.y(),width/2+30,height/2+17) || DeskControls.hit(e.x(),e.y(),width-22,22)) deletion=null;
            else if(DeskControls.hit(e.x(),e.y(),width/2-30,height/2+17)) try {
                session.update(session.album().remove(deletion));deletion=null;spread=Math.min(spread,spreadCount()-1);prepare();error="";
            } catch(Exception ex) {error="删除失败："+ex.getMessage();}
            return true;
        }
        if(DeskControls.hit(e.x(),e.y(),width-22,22)) {onClose();return true;}
        if(DeskControls.hit(e.x(),e.y(),bx+20,by+bh+18)) {turn(-1);return true;}
        if(DeskControls.hit(e.x(),e.y(),bx+bw-20,by+bh+18)) {turn(1);return true;}
        for(var slot:slots(spread)) if(slot.hit(e.x(),e.y())) {
            var card=session.album().cards().get(slot.index);
            if(DeskControls.hit(e.x(),e.y(),slot.deleteX(),slot.deleteY())) deletion=card.id();
            else try {session.update(session.album().select(card.id()));onClose();} catch(Exception ex) {error="读取失败："+ex.getMessage();}
            return true;
        }
        return true;
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if(e.key()==256) {onClose();return true;}
        if(deletion==null) {if(e.key()==263) {turn(-1);return true;}if(e.key()==262) {turn(1);return true;}}
        return true;
    }
    @Override public void onClose() {if(deletion!=null) {deletion=null;return;}if(!turning()) minecraft.setScreen(parent);}
    @Override public void removed() {for(var id:thumbnails.values()) minecraft.getTextureManager().release(id);thumbnails.clear();}
    @Override public boolean isPauseScreen() {return false;}
}
