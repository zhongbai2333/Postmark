package dev.postmark.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.postmark.Postmark;
import dev.postmark.compat.SignMeUpBridge;
import dev.postmark.model.*;
import dev.postmark.render.PostcardPainter;
import dev.postmark.storage.ImageFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class PostcardScreen extends Screen {
    private static final int INK=0xFF263E34, MUTED=0xFF576D5E, GREEN=0xFF365E49;
    private static final double STAMP_SIZE=.16;
    private static final float UI_SCALE=.8f;
    private final Screen parent;
    private final ClientSession session;
    private final String requested;
    private final Map<String,Identifier> textures = new HashMap<>();
    private final Map<String,BufferedImage> images = new HashMap<>();
    private Identifier canvas, sendCanvas,landingTexture;
    private dev.postmark.render.StampRaster.Layer landingLayer;
    private LandingKey landingKey;
    private record LandingKey(String asset,int width,int height,double x,double y,double size,double angle) {}
    private StampDefinition tool;
    private boolean erasing, dragging, clickHeld;
    private double pickupX,pickupY;
    private double toolSize=STAMP_SIZE, resizeX, resizeY, resizeSize, cursorOffsetX, cursorOffsetY;
    private boolean resizing;
    private CardTurn cardTurn;
    private PhotoImportChoice photoChoice;
    private record CardTurn(Identifier previous,long start,int direction,double previousW,double previousH) {}
    private final dev.postmark.client.StampPreviewTimer previewTimer=new dev.postmark.client.StampPreviewTimer();
    private double angle, toolX, toolY;
    private final SmoothShelfScroll shelfScroll=new SmoothShelfScroll();
    private StampBag bag;
    private CollectionTag collectionTag;
    private boolean bagPickup;
    private double cardX, cardY, cardW, cardH;
    private String status="";
    private long statusAt,lastCollectionSound;
    private PendingStamp pressing;
    private long sendAt;
    private long flyAt;
    private boolean exporting, packing;
    private final List<ReturningTool> returning=new ArrayList<>();
    private record ReturningTool(StampDefinition stamp,double x,double y,double angle,double size,long start,long duration) {}
    private Path exported;
    private int textureCounter;
    private record PendingStamp(StampDefinition stamp,double x,double y,double angle,double size,long start,boolean committed) {
        PendingStamp markCommitted() { return new PendingStamp(stamp,x,y,angle,size,start,true); }
    }
    public PostcardScreen(Screen parent, ClientSession session, String requested) {
        super(Component.translatable("screen.postmark.title"));
        this.parent=parent; this.session=session; this.requested=requested;
        if (!session.album().stamps().isEmpty()) tool=session.album().stamps().getFirst();
    }
    public static void show(Screen parent,String requested) {
        var mc=Minecraft.getInstance();
        try { mc.setScreen(new PostcardScreen(parent,ClientSession.get(),requested)); }
        catch (Exception e) {
            Postmark.LOGGER.error("Unable to load postcard album",e);
            mc.getChatListener().handleSystemMessage(Component.literal("明信片读取失败，原草稿已保留："+e.getMessage()),false);
        }
    }
    private static long now() { return System.nanoTime()/1_000_000; }
    private String tr(String key) { return Component.translatable("postmark."+key).getString(); }
    private void say(String text) { status=text; statusAt=now(); }
    @Override protected void init() {
        layout();
        if(bag==null) bag=new StampBag(font,this::drawBagFace,this::takeFromBag);
        bag.layout(width,height);addWidget(bag.search);
        if(collectionTag==null) collectionTag=new CollectionTag(font,this::takeMilestoneTicket);
        collectionTag.layout(width,height);collectionTag.sync(session.album().stamps());
        if(bag.isOpen()) setFocused(bag.search);
        if(dragging) previewTimer.reset(toolX,toolY,angle,now());
        if(canvas==null) try { refresh(); } catch(Exception e) { fail(e); }
    }
    private void layout() {
        var viewport=dev.postmark.render.PaperViewport.fit(width,height,card().aspectRatio());
        cardX=viewport.x();cardY=viewport.y();cardW=viewport.width();cardH=viewport.height();
    }
    private boolean busy() { return photoChoice!=null || pressing!=null || exporting || packing || cardTurn!=null; }
    private Postcard card() { return session.album().selected(); }
    private BufferedImage image(String asset) throws IOException {
        BufferedImage image=images.get(asset);
        if(image==null) { image=session.store().image(asset); images.put(asset,image); }
        return image;
    }
    private Identifier texture(String asset) throws IOException {
        Identifier texture=textures.get(asset);
        if(texture==null) { texture=upload(image(asset)); textures.put(asset,texture); }
        return texture;
    }
    private Identifier upload(BufferedImage image) {
        NativeImage nativeImage=new NativeImage(image.getWidth(),image.getHeight(),false);
        for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) nativeImage.setPixel(x,y,image.getRGB(x,y));
        Identifier id=Identifier.fromNamespaceAndPath("postmark","dynamic/"+System.identityHashCode(this)+"/"+textureCounter++);
        minecraft.getTextureManager().register(id,new DynamicTexture(()->"Postmark artwork",nativeImage));
        return id;
    }
    private void refresh() throws IOException {
        BufferedImage image=PostcardPainter.paint(card(),this::image);
        Identifier next=upload(image);
        if(canvas!=null) minecraft.getTextureManager().release(canvas);
        canvas=next;layout();
    }
    private void commit(Album next) throws IOException { session.update(next); refresh(); }
    private void turnTo(Album next,int direction) throws IOException {
        if(next.current().equals(session.album().current())) return;
        Identifier nextCanvas=upload(PostcardPainter.paint(next.selected(),this::image));
        try { session.update(next); }
        catch(IOException e) { minecraft.getTextureManager().release(nextCanvas); throw e; }
        if(dragging) returnTool(toolX,toolY);
        erasing=false;
        cardTurn=new CardTurn(canvas,now(),direction,cardW,cardH);
        canvas=nextCanvas;layout();
    }
    private void finishTurn() {
        if(cardTurn!=null) {
            if(cardTurn.previous!=null) minecraft.getTextureManager().release(cardTurn.previous);
            cardTurn=null;
        }
    }
    private int arrowHit(double x,double y) {
        if(Math.abs(y-(cardY+cardH/2))>22) return 0;
        if(Math.abs(x-(cardX-26))<20) return -1;
        if(Math.abs(x-(cardX+cardW+26))<20) return 1;
        return 0;
    }
    private void drawArrow(GuiGraphicsExtractor g,int direction,boolean hovered) {
        int x=(int)(direction<0?cardX-26:cardX+cardW+26),y=(int)(cardY+cardH/2);
        boolean enabled=session.album().cards().size()>1;
        int color=enabled?(hovered?0xE8FFF3D7:0x99EADFC6):0x44EADFC6;
        // Three-pixel steps keep the pale chevron legible against a blurred world.
        for(int row=-3;row<=3;row++) {
            int dx=(Math.abs(row)-1)*3*-direction;
            g.fill(x+dx-2,y+row*3-1,x+dx+2,y+row*3+2,color);
        }
    }
    private void drawPaper(GuiGraphicsExtractor g,int x,int y) {
        if(cardTurn==null) {
            g.fill(x+5,y+7,x+(int)cardW+6,y+(int)cardH+7,0x3030271D);
            if(canvas!=null) blit(g,canvas,x,y,(int)cardW,(int)cardH);
            return;
        }
        double t=Math.clamp((now()-cardTurn.start)/560.0,0,1);
        // The incoming sheet first peeks out behind the old one, then settles on top.
        double lift=Math.sin(Math.PI*t), settle=ease((t-.42)/.58);
        int nextX=x+(int)(cardTurn.direction*cardW*.24*lift);
        int nextY=y-(int)(10*(1-settle)+cardH*.075*lift);
        float scale=(float)(.94+.06*settle);
        if(t<.5) drawTurningSheet(g,canvas,nextX,nextY,scale,1,cardW,cardH);
        int oldX=(int)((width-cardTurn.previousW)/2+12)-(int)(cardTurn.direction*cardTurn.previousW*.15*ease(t));
        int oldY=(int)((height-cardTurn.previousH)/2)+(int)(12*ease(t));
        drawTurningSheet(g,cardTurn.previous,oldX,oldY,1f,(float)(1-ease((t-.40)/.60)),cardTurn.previousW,cardTurn.previousH);
        if(t>=.5) drawTurningSheet(g,canvas,nextX,nextY,scale,1,cardW,cardH);
    }
    private void drawTurningSheet(GuiGraphicsExtractor g,Identifier id,int x,int y,float scale,float opacity,double paperW,double paperH) {
        if(id==null || opacity<=0) return;
        int w=(int)(paperW*scale),h=(int)(paperH*scale);
        x+=(int)(paperW-w)/2;y+=(int)(paperH-h)/2;
        g.fill(x+4,y+6,x+w+4,y+h+6,((int)(opacity*36)<<24)|0x30271D);
        blitAlpha(g,id,x,y,w,h,opacity);
    }
    private void fail(Exception error) {
        Postmark.LOGGER.warn("Postcard operation failed",error);
        say(tr("failed")+": "+error.getMessage());
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        if(minecraft.level==null) extractPanorama(g,partialTick);
        extractBlurredBackground(g);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        layout();
        g.fill(0,0,width,height,0x58303D35);
        if(packing || exporting) { drawSend(g,mouseX,mouseY); return; }
        if(photoChoice!=null) { drawPaper(g,(int)cardX,(int)cardY);photoChoice.render(g,font,mouseX,mouseY,width,height);return; }
        // A stitched leather roll stays at the left edge; empty loops remain after tools are lifted.
        g.pose().pushMatrix(); g.pose().translate(0,bagOffset()); g.pose().scale(UI_SCALE,UI_SCALE);
        int bagTop=24,bagBottom=height-36;
        g.fill(5,bagTop+6,86,bagBottom+5,0x39302119);
        g.fill(0,bagTop,80,bagBottom,0xFF624735);
        g.fill(0,bagTop+3,76,bagBottom-3,0xFF896647);
        g.fill(0,bagTop+5,73,bagBottom-6,0xFF9B7853);
        g.fill(70,bagTop+6,76,bagBottom-5,0xFF71513A);
        for(int sy=bagTop+10;sy<bagBottom-8;sy+=7) g.fill(66,sy,68,sy+3,0xFFC6A578);
        var stamps=session.album().stamps();
        updateShelfBounds(); shelfScroll.advance(now());
        double offset=shelfScroll.position();
        int first=Math.max(0,(int)Math.floor(offset/68)-1);
        int last=Math.min(stamps.size(),(int)Math.ceil((offset+height-72)/68)+1);
        g.enableScissor(0,bagTop+5,79,bagBottom-6);
        g.pose().pushMatrix();g.pose().translate(0,(float)-offset);
        String hover="";
        for(int i=first;i<last;i++) {
            var stamp=stamps.get(i); int sy=70+i*68;
            g.fill(14,sy-20,61,sy+24,0xFF75543D);
            g.fill(17,sy-18,58,sy+21,0xFF493B2D);
            if(!(dragging && tool!=null && tool.key().equals(stamp.key())) && !(pressing!=null && pressing.stamp.key().equals(stamp.key())) && returning.stream().noneMatch(r->r.stamp.key().equals(stamp.key())))
                drawTool(g,stamp,38,sy,0,38,1);
            g.fill(10,sy+10,64,sy+24,0xFFA58055);
            g.fill(10,sy+10,64,sy+13,0xFFC09A6B);
            g.fill(10,sy+23,64,sy+25,0xFF654830);
            for(int sx=15;sx<62;sx+=7) g.fill(sx,sy+19,sx+3,sy+20,0xFFDAC298);
            if(overBag(mouseX,mouseY) && Math.abs(bagY(mouseY)+offset-sy)<32) hover=stamp.practice()?tr("practice_"+stamp.key().substring(9)):stamp.name()+(stamp.expert()?" · 大师章":"");
        }
        g.pose().popMatrix();g.disableScissor();
        if(shelfScroll.maximum()>0) {
            g.fill(72,bagTop+10,74,bagBottom-10,0xFF4D3E2E);
            int sy=bagTop+10+(int)((bagBottom-bagTop-32)*offset/shelfScroll.maximum());
            g.fill(71,sy,75,sy+12,0xFFD1B77F);
        }
        g.pose().popMatrix();
        double press=pressing==null?0:Math.max(0,1-Math.abs((now()-pressing.start-120)/120.0));
        int px=(int)cardX,py=(int)(cardY+press*1.5);
        drawPaper(g,px,py);
        int arrow=arrowHit(mouseX,mouseY);
        drawArrow(g,-1,arrow==-1 && !busy());drawArrow(g,1,arrow==1 && !busy());
        if(arrow!=0) hover=session.album().cards().size()>1?(arrow<0?"上一张信纸":"下一张信纸"):"只有一张信纸 · N 新建";
        // A corner of an actual envelope is the send affordance.
        int ex=(int)cornerX(),ey=(int)cornerY(),ew=(int)cornerW(),eh=(int)(cornerW()/card().aspectRatio());
        g.pose().pushMatrix(); g.pose().translate(ex,ey); g.pose().scale(UI_SCALE,UI_SCALE); g.pose().translate(-ex,-ey); g.pose().rotateAbout(-.12f,ex+ew/2f,ey+eh/2f);
        g.fill(ex+3,ey+4,ex+ew+3,ey+eh+4,0x33362D20);
        g.fill(ex,ey,ex+ew,ey+eh,0xFFDBC8A6);
        triangle(g,ex,ey,ex+ew,ey,ex+ew/2,ey+eh*3/5,0xFFF0E0C0);
        line(g,ex,ey,ex+ew/2,ey+eh*3/5,0xFFB6A17E); line(g,ex+ew/2,ey+eh*3/5,ex+ew,ey,0xFFB6A17E);
        int mark=Math.max(1,Math.min(ew,eh)/5);g.fill(ex+ew-mark-8,ey+8,ex+ew-8,ey+8+mark,0xFF9DAB8A); g.pose().popMatrix();
        if(envelopeHit(mouseX,mouseY)) hover="装进信封";
        // An eraser sits on the desk, with no toolbar around it.
        int erx=(int)cardX-34,ery=(int)(cardY+cardH)-12;
        if(!erasing) drawEraser(g,erx,ery,false);
        if(Math.abs(mouseX-erx)<16 && Math.abs(mouseY-ery)<15) hover=erasing?"放回橡皮 · E":"拿起橡皮 · E";
        if(erasing && !busy()) {
            if(overCard(toolX,toolY)) {
                Imprint hit=card().topAt(nx(toolX),ny(toolY));
                if(hit!=null) { int size=(int)(hit.size()*cardW),cx=(int)(cardX+hit.x()*cardW),cy=(int)(cardY+hit.y()*cardH);
                    g.pose().pushMatrix(); g.pose().rotateAbout((float)hit.angle(),cx,cy); g.outline(cx-size/2,cy-size/2,size,size,0xFFB56E53); g.pose().popMatrix(); }
            }
            drawEraser(g,toolX,toolY,true);
        }
        collectionTag.layout(width,height);collectionTag.render(g,mouseX,mouseY);
        for(var r:returning) { double t=ease((now()-r.start)/(double)r.duration);
            if(isTicket(r.stamp)) {
                double tx=collectionTag.anchorX(),ty=collectionTag.anchorY();
                drawTool(g,r.stamp,r.x+(tx-r.x)*t,r.y+(ty-r.y)*t-Math.sin(Math.PI*t)*18,r.angle*(1-t),(int)(r.size*cardW*(1-t)+30*t),1,(float)(1-t*.7));
                continue;
            }
            int index=0;
            for(int i=0;i<session.album().stamps().size();i++) if(session.album().stamps().get(i).key().equals(r.stamp.key())) { index=i;break; }
            double targetX=38*UI_SCALE,targetY=bagOffset()+(70+index*68-shelfScroll.position())*UI_SCALE;
            double rx=r.x+(targetX-r.x)*t,ry=r.y+(targetY-r.y)*t-Math.sin(Math.PI*t)*24;
            int from=Math.max(8,(int)(r.size*cardW)),to=(int)(38*UI_SCALE);
            // At the mouth, use the same clipping and front pocket as the stored tool.
            boolean atShelf=t>.85 && rx<79*UI_SCALE;
            if(atShelf) g.enableScissor(0,(int)(bagOffset()+29*UI_SCALE),(int)(79*UI_SCALE),(int)(bagOffset()+(height-42)*UI_SCALE));
            drawTool(g,r.stamp,rx,ry,r.angle*(1-t),(int)(from+(to-from)*t),1);
            if(atShelf) g.disableScissor();
            g.pose().pushMatrix();g.pose().translate(0,bagOffset());g.pose().scale(UI_SCALE,UI_SCALE);
            g.enableScissor(0,29,79,height-42);
            g.pose().translate(0,(float)-shelfScroll.position());int sy=70+index*68;
            g.fill(10,sy+10,64,sy+24,0xFFA58055);g.fill(10,sy+10,64,sy+13,0xFFC09A6B);
            g.fill(10,sy+23,64,sy+25,0xFF654830);
            for(int sx=15;sx<62;sx+=7) g.fill(sx,sy+19,sx+3,sy+20,0xFFDAC298);
            g.disableScissor();g.pose().popMatrix();
        }
        if(!erasing && tool!=null && (dragging || pressing!=null)) {
            double tx=pressing!=null?cardX+pressing.x*cardW:toolX,ty=pressing!=null?cardY+pressing.y*cardH:toolY;
            float preview=stampPreviewAmount();
            if(preview>0) drawLandingPreview(g,preview);
            drawTool(g,pressing!=null?pressing.stamp:tool,tx,ty,pressing!=null?pressing.angle:angle,Math.max(8,(int)((pressing!=null?pressing.size:toolSize)*cardW)),press,1-.72f*preview);
        }
        int claspY=(int)(bagOffset()+6*UI_SCALE);
        g.fill(14,claspY,48,claspY+19,0xFF614631);g.fill(18,claspY+3,44,claspY+16,0xFFC39A5D);
        g.fill(21,claspY+5,41,claspY+14,0xFF735439);
        for(int dotX=26;dotX<=34;dotX+=4) g.fill(dotX,claspY+9,dotX+2,claspY+11,0xFFF5DDA2);
        if(bagClaspHit(mouseX,mouseY)) hover="解开袋口，找印章 · Tab";
        g.pose().pushMatrix();g.pose().translate((float)(cardX+cardW+34),(float)(cardY-26));g.pose().rotate(-.06f);g.pose().scale(2f,2f);
        DeskControls.draw(g,DeskControls.Kind.BOOK,0,0,true);g.pose().popMatrix();
        DeskControls.draw(g,DeskControls.Kind.NEW_PAPER,(int)(cardX+cardW-14),(int)cardY-26,true);
        DeskControls.draw(g,DeskControls.Kind.PLAIN_PAPER,(int)(cardX+cardW-48),(int)cardY-26,card().background()!=null);
        DeskControls.draw(g,DeskControls.Kind.FOLDER,(int)(cardX+cardW+32),(int)(cardY+cardH-54),true);
        DeskControls.draw(g,DeskControls.Kind.CLOSE,width-22,22,true);
        GuideEntry.draw(g,GuideEntry.deskX(width),GuideEntry.deskY(height),GuideEntry.hit(mouseX,mouseY,width,height));
        if(hover.isEmpty() && overCard(mouseX,mouseY)) hover=dragging?(isTicket(tool)?"左键放下纪念票 · 右键拖动调大小 · 滚轮旋转":"左键盖印 · 右键拖动调大小 · 滚轮旋转"):erasing?"点击擦除最上层印迹":"拖入 PNG/JPG 更换底片";
        String tagHint=collectionTag.hint(mouseX,mouseY);if(!tagHint.isEmpty() && !dragging && !erasing) hover=tagHint;
        if(bookHit(mouseX,mouseY)) hover="打开收集册，翻阅或删除明信片";
        if(GuideEntry.hit(mouseX,mouseY,width,height))hover="展开漫游志";
        if(DeskControls.hit(mouseX,mouseY,cardX+cardW-14,cardY-26)) hover="新建信纸 · N";
        if(DeskControls.hit(mouseX,mouseY,cardX+cardW-48,cardY-26)) hover="恢复素色信纸 · B";
        if(DeskControls.hit(mouseX,mouseY,cardX+cardW+32,cardY+cardH-54)) hover="打开导出目录 · F";
        if(DeskControls.hit(mouseX,mouseY,width-22,22)) hover="收起明信片 · Esc";
        boolean recent=!status.isEmpty() && now()-statusAt<4500;
        if(recent && mouseY>=height-26 && Math.abs(mouseX-width/2)<120) hover=status;
        if(!bag.isOpen() && !busy()) HoverHint.draw(g,font,hover.isEmpty() && recent?shortStatus():hover,width,height);
        if(bag!=null && bag.isOpen()) { bag.layout(width,height);bag.render(g,mouseX,mouseY,partialTick,session.album().stamps()); }
    }
    private boolean bookHit(double x,double y) { return Math.abs(x-(cardX+cardW+34))<=27 && Math.abs(y-(cardY-26))<=28; }
    public void pickUpCollected(String key) {
        session.album().stamps().stream().filter(s->s.key().equals(key)).findFirst().ifPresent(s->{
            takeFromBag(s);bagPickup=false;clickHeld=false;
            toolX=minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());toolY=minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
        });
    }
    private boolean bagClaspHit(double x,double y) { return x>=0 && x<64 && bagY(y)>=0 && bagY(y)<36; }
    private void openBag() {
        collectionTag.close();
        if(dragging) returnTool(toolX,toolY);
        erasing=false;bag.open(session.album().stamps());setFocused(bag.search);
    }
    public StampBag stampBag() { return bag; }
    public CollectionTag collectionTag() { return collectionTag; }
    private static boolean isTicket(StampDefinition stamp) { return stamp.key().startsWith("postmark:milestone/"); }
    private void takeMilestoneTicket(int milestone) {
        if(!collectionTag.progress().unlocks(milestone)) return;
        edit(()->{
            String asset=session.store().putImage(dev.postmark.render.MilestoneTicketPainter.paint(milestone));
            takeFromBag(new StampDefinition("postmark:milestone/"+milestone,"纪念票 · "+milestone,asset,true));
            toolSize=.28;
        });
    }
    private void takeFromBag(StampDefinition stamp) {
        if(!available(stamp))return;
        revealShelfStamp(stamp);
        setFocused(null);tool=stamp;returning.removeIf(r->r.stamp.key().equals(stamp.key()));
        erasing=false;dragging=true;clickHeld=true;bagPickup=true;resizing=false;angle=0;toolSize=STAMP_SIZE;
        cursorOffsetX=cursorOffsetY=0;pickupX=toolX;pickupY=toolY;
        previewTimer.reset(toolX,toolY,angle,now());
    }
    private void drawBagFace(GuiGraphicsExtractor g,StampDefinition stamp,double x,double y,double size,float opacity) {
        try {
            String key="bag/"+stamp.asset()+"/"+stamp.expert();Identifier id=textures.get(key);
            if(id==null) {
                BufferedImage art=image(stamp.asset()),face=new BufferedImage(48,48,BufferedImage.TYPE_INT_ARGB);
                var pen=face.createGraphics();
                pen.setColor(new java.awt.Color(0x30271E));pen.fillRect(3,5,44,42);
                pen.setColor(new java.awt.Color(stamp.expert()?0xB28A3C:0xA07C53));pen.fillRect(1,1,44,44);
                pen.setColor(new java.awt.Color(stamp.expert()?0xF1D383:0xDBC6A0));pen.fillRect(3,3,40,40);
                pen.setColor(new java.awt.Color(0xEAE0C7));pen.fillRect(6,6,34,34);
                int aw=art.getWidth(),ah=art.getHeight();double scale=32.0/Math.max(aw,ah);
                pen.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                int dw=Math.max(1,(int)(aw*scale)),dh=Math.max(1,(int)(ah*scale));pen.drawImage(art,(46-dw)/2,(46-dh)/2,dw,dh,null);pen.dispose();
                id=upload(face);textures.put(key,id);
            }
            double tilt=((stamp.key().hashCode()&7)-3)*.025;
            g.pose().pushMatrix();g.pose().translate((float)x,(float)y);g.pose().rotate((float)tilt);
            blitAlpha(g,id,-(int)size/2,-(int)size/2,(int)size,(int)size,opacity);g.pose().popMatrix();
        } catch(IOException ignored) {}
    }
    private String shortStatus() {
        if(status.contains("失败") || status.contains("不可用") || status.contains("超时")) return "未完成";
        if(status.contains("已保存") || status.contains("saved") || status.contains("saved.")) return "已保存";
        return "·";
    }
    private void revealShelfStamp(StampDefinition stamp) {
        if(isTicket(stamp)) return;
        updateShelfBounds();
        int index=0;
        for(int i=0;i<session.album().stamps().size();i++) if(session.album().stamps().get(i).key().equals(stamp.key())) { index=i;break; }
        shelfScroll.reveal(70+index*68,55,Math.max(55,height-64));
    }
    private void updateShelfBounds() { shelfScroll.bounds(session.album().stamps().size()*68.0-Math.max(1,height-78)); }
    public double shelfScrollPosition() { return shelfScroll.position(); }
    private float bagOffset() { return height*(1-UI_SCALE)/2; }
    private double bagY(double y) { return (y-bagOffset())/UI_SCALE; }
    private boolean overBag(double x,double y) { return x>=0 && x<80*UI_SCALE && bagY(y)>=36 && bagY(y)<height-36; }
    private double cornerW() {return Math.min(80,60*card().aspectRatio());}
    private double cornerX() {return cardX+cardW-cornerW()*.71*UI_SCALE;}
    private double cornerY() {return cardY+cardH-cornerW()/card().aspectRatio()*.64*UI_SCALE;}
    private boolean envelopeHit(double x,double y) {return x>=cornerX()-4 && x<=cornerX()+cornerW()*UI_SCALE+4 && y>=cornerY()-4 && y<=cornerY()+cornerW()/card().aspectRatio()*UI_SCALE+4;}
    private void drawEraser(GuiGraphicsExtractor g,double x,double y,boolean held) {
        g.pose().pushMatrix(); g.pose().translate((float)x,(float)y); g.pose().scale(UI_SCALE,UI_SCALE); g.pose().rotate(held?-.35f:-.18f);
        g.fill(-8,held?1:-4,15,held?14:10,0x38312522);
        g.fill(-11,-9,11,8,0xFF986453); g.fill(-12,-12,10,4,0xFFDFA78F);
        g.fill(-11,-11,9,-9,0xFFF0C2A6); g.fill(7,-8,10,4,0xFFC78C75);
        // Folded paper sleeve, with exposed pink rubber at both ends.
        g.fill(-13,-6,11,2,0xFFF2E9CD);
        g.fill(-13,2,11,7,0xFFBEB798);
        g.fill(-12,-6,10,-4,0xFFFFF5DD);
        g.fill(-13,-4,-9,2,0xFF548072);g.fill(7,-4,11,2,0xFF548072);
        g.fill(-13,2,-9,6,0xFF3E635B);g.fill(7,2,11,6,0xFF3E635B);
        g.fill(-6,-3,4,-2,0xFF5B7465);g.fill(-6,-1,1,0,0xFF5B7465);
        g.fill(-12,6,10,7,0xFF8B927C);
        g.pose().popMatrix();
    }
    private void drawTool(GuiGraphicsExtractor g,StampDefinition stamp,double x,double y,double angle,int size,double press) {
        drawTool(g,stamp,x,y,angle,size,press,1f);
    }
    private void drawTool(GuiGraphicsExtractor g,StampDefinition stamp,double x,double y,double angle,int size,double press,float opacity) {
        if(isTicket(stamp)) {
            g.pose().pushMatrix();g.pose().translate((float)x,(float)y);g.pose().rotate((float)angle);
            try { blitAlpha(g,texture(stamp.asset()),-size/2,-size/2-(int)(4*(1-press)),size,size,opacity); } catch(IOException ignored) {}
            g.pose().popMatrix();return;
        }
        String key="tool/"+stamp.asset()+"/"+stamp.expert();
        Identifier id=textures.get(key);
        try {
            if(id==null) { id=upload(dev.postmark.render.StampToolPainter.paint(stamp,image(stamp.asset()))); textures.put(key,id); }
        } catch(IOException e) { return; }
        float scale=size/64f;
        g.pose().pushMatrix(); g.pose().translate((float)x,(float)y); g.pose().rotate((float)angle); g.pose().scale(scale,scale);
        blitAlpha(g,id,-40,-64-(int)(14*(1-press)),80,104,opacity);
        g.pose().popMatrix();
    }
    private static void blitAlpha(GuiGraphicsExtractor g,Identifier id,int x,int y,int w,int h,float opacity) {
        int color=(Math.clamp(Math.round(opacity*255),0,255)<<24)|0xFFFFFF;
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,id,x,y,0f,0f,w,h,1,1,1,1,color);
    }
    public float stampPreviewAmount() { return dragging && !erasing && tool!=null?previewTimer.amount(now()):0; }
    private void drawLandingPreview(GuiGraphicsExtractor g,float amount) {
        if(!overCard(toolX,toolY)) return;
        var key=new LandingKey(tool.asset(),card().width(),card().height(),nx(toolX),ny(toolY),toolSize,angle);
        try {
            if(!key.equals(landingKey)) {
                var layer=dev.postmark.render.StampRaster.paint(key.width,key.height,image(key.asset),key.x,key.y,key.size,key.angle);
                var texture=upload(layer.image());
                releaseLandingPreview();landingTexture=texture;landingLayer=layer;landingKey=key;
            }
        } catch(IOException e) {return;}
        g.enableScissor((int)cardX,(int)cardY,(int)(cardX+cardW),(int)(cardY+cardH));
        g.pose().pushMatrix();g.pose().translate((float)cardX,(float)cardY);
        // Keep the same document-pixel sampling phase as the complete postcard texture.
        g.pose().scale((float)(cardW/card().width()),(float)(cardH/card().height()));
        blitAlpha(g,landingTexture,landingLayer.x(),landingLayer.y(),landingLayer.image().getWidth(),landingLayer.image().getHeight(),.48f*amount);
        g.pose().popMatrix();g.disableScissor();
    }
    private void releaseLandingPreview() {
        if(landingTexture!=null) minecraft.getTextureManager().release(landingTexture);
        landingTexture=null;landingLayer=null;landingKey=null;
    }
    private static void blit(GuiGraphicsExtractor g,Identifier id,int x,int y,int w,int h) {
        g.blit(id,x,y,x+w,y+h,0f,1f,0f,1f);
    }
    @FunctionalInterface private interface Operation { void run() throws Exception; }
    private void edit(Operation op) { try { op.run(); } catch(Exception e) { fail(e); } }
    private boolean overCard(double x,double y) { return x>=cardX && x<=cardX+cardW && y>=cardY && y<=cardY+cardH; }
    private double nx(double x) { return (x-cardX)/cardW; }
    private double ny(double y) { return (y-cardY)/cardH; }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice) {
        if(photoChoice!=null) {
            if(e.button()==0) {
                int choice=photoChoice.hit(e.x(),e.y(),width,height);
                if(choice==2) cancelPhoto();
                else if(choice>=0) edit(()->{
                    var photo=photoChoice.photo();String asset=session.store().putImage(photo);
                    commit(session.album().replace(card().withBackground(asset,photo.getWidth(),photo.getHeight(),choice==0)));
                    cancelPhoto();say(tr("photo_added"));
                });
            }
            return true;
        }
        if(busy()) return true;
        if(bag.isOpen()) { toolX=e.x();toolY=e.y();return bag.click(e); }
        if(!dragging && !erasing) {
            toolX=e.x();toolY=e.y();
            if(collectionTag.click(e)) return true;
            if(collectionTag.isOpen()) collectionTag.close();
        }
        if(e.button()==0 && bagClaspHit(e.x(),e.y())) { openBag();return true; }
        if(e.button()==0 && !resizing) {
            if(GuideEntry.hit(e.x(),e.y(),width,height)) {if(dragging)returnTool(toolX,toolY);erasing=false;TravelGuideScreen.show(this);return true;}
            if(DeskControls.hit(e.x(),e.y(),width-22,22)) { if(dragging) returnTool(toolX,toolY);else onClose();return true; }
            if(bookHit(e.x(),e.y())) { minecraft.setScreen(new PostcardAlbumScreen(this,session));return true; }
            if(DeskControls.hit(e.x(),e.y(),cardX+cardW-14,cardY-26)) { edit(()->turnTo(session.album().addCard(),1));return true; }
            if(DeskControls.hit(e.x(),e.y(),cardX+cardW-48,cardY-26)) { edit(()->commit(session.album().replace(card().withBackground(null))));return true; }
            if(DeskControls.hit(e.x(),e.y(),cardX+cardW+32,cardY+cardH-54)) { Util.getPlatform().openPath(exportDirectory());return true; }
        }
        if(e.button()==GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if(dragging) { resizing=true;resizeX=e.x();resizeY=e.y();resizeSize=toolSize; }
            else erasing=false;
            return true;
        }
        if(e.button()!=GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if(resizing) return true;
        int direction=arrowHit(e.x(),e.y());
        if(direction!=0) { edit(()->turnTo(session.album().select(direction),direction));return true; }
        if(envelopeHit(e.x(),e.y())) { send(); return true; }
        if(Math.abs(e.x()-(cardX-34))<16 && Math.abs(e.y()-(cardY+cardH-12))<15) { erasing=!erasing; dragging=false; clickHeld=false; resizing=false; bagPickup=false; toolX=e.x(); toolY=e.y(); return true; }
        if(overBag(e.x(),e.y())) {
            int index=(int)Math.floor((bagY(e.y())-36+shelfScroll.position())/68);
            if(index>=0 && index<session.album().stamps().size()) {
                tool=session.album().stamps().get(index); returning.removeIf(r->r.stamp.key().equals(tool.key())); erasing=false; dragging=true; clickHeld=false; angle=0;
                toolX=e.x(); toolY=e.y(); pickupX=e.x(); pickupY=e.y();
                toolSize=STAMP_SIZE;resizing=false;bagPickup=false;cursorOffsetX=cursorOffsetY=0;
                previewTimer.reset(toolX,toolY,angle,now());
            }
            return true;
        }
        if(dragging && clickHeld) { moveTool(e.x(),e.y());placeTool(toolX,toolY); return true; }
        if(overCard(e.x(),e.y()) && erasing) {
            Imprint hit=card().topAt(nx(e.x()),ny(e.y()));
            if(hit!=null) edit(()->{ commit(session.album().replace(card().erase(hit.id()))); sound(false); });
            return true;
        }
        return super.mouseClicked(e,twice);
    }
    private void moveTool(double x,double y) {
        if(resizing) {
            toolSize=Math.clamp(resizeSize*Math.exp(((x-resizeX)-(y-resizeY))/140.0),.02,.5);
            return;
        }
        if(dragging) { x+=cursorOffsetX;y+=cursorOffsetY;previewTimer.update(x,y,angle,now()); }
        toolX=x; toolY=y;
    }
    @Override public void mouseMoved(double x,double y) { if(!bag.isOpen() && (dragging || erasing)) moveTool(x,y); }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy) {
        if(bag.isOpen()) { bag.drag(e);return true; }
        if(bagPickup && Math.hypot(e.x()-pickupX,e.y()-pickupY)>4) bagPickup=false;
        if(dragging || erasing) { moveTool(e.x(),e.y()); return true; }
        return false;
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) {
        if(bag.isOpen()) { bag.release();return true; }
        if(bagPickup && e.button()==0) { bagPickup=false;return true; }
        if(e.button()==GLFW.GLFW_MOUSE_BUTTON_RIGHT && resizing) {
            moveTool(e.x(),e.y());resizing=false;clickHeld=true;
            cursorOffsetX=toolX-e.x();cursorOffsetY=toolY-e.y();return true;
        }
        if(e.button()!=GLFW.GLFW_MOUSE_BUTTON_LEFT || !dragging) return false;
        if(resizing) { clickHeld=true;return true; }
        if(!clickHeld && overBag(e.x(),e.y()) && Math.hypot(e.x()-pickupX,e.y()-pickupY)<4) {
            clickHeld=true; return true;
        }
        moveTool(e.x(),e.y());placeTool(toolX,toolY); return true;
    }
    private void placeTool(double x,double y) {
        dragging=false; clickHeld=false; resizing=false; bagPickup=false;
        if(tool!=null && !available(tool)){tool=null;return;}
        if(tool!=null && overCard(x,y) && card().imprints().size()<Postcard.MAX_IMPRINTS)
            pressing=new PendingStamp(tool,nx(x),ny(y),angle,toolSize,now(),false);
        else { if(overCard(x,y)) say(tr("full")); returnTool(x,y); }
    }
    private ReturningTool returningTool(StampDefinition stamp,double x,double y,double rotation,double size) {
        return new ReturningTool(stamp,x,y,rotation,size,now(),Math.max(240,shelfScroll.settlingMillis()+80));
    }
    private void returnTool(double x,double y) {
        dragging=false; clickHeld=false; resizing=false; bagPickup=false;
        if(tool!=null) { revealShelfStamp(tool);returning.removeIf(r->r.stamp.key().equals(tool.key())); returning.add(returningTool(tool,x,y,angle,toolSize)); }
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        if(busy()) return true;
        if(bag.isOpen()) { bag.scroll(dy);return true; }
        if(x<80*UI_SCALE) { updateShelfBounds();shelfScroll.scroll(dy); }
        else if(!erasing && dragging) { angle=Math.IEEEremainder(angle+Math.toRadians(dy*5),Math.PI*2); previewTimer.update(toolX,toolY,angle,now()); }
        return true;
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if(photoChoice!=null) { if(e.key()==GLFW.GLFW_KEY_ESCAPE) cancelPhoto();return true; }
        if(bag.isOpen()) {
            if(e.key()==GLFW.GLFW_KEY_ESCAPE || e.key()==GLFW.GLFW_KEY_TAB) { bag.close();setFocused(null);return true; }
            bag.search.setFocused(true);setFocused(bag.search);bag.search.keyPressed(e);return true;
        }
        if(e.key()==GLFW.GLFW_KEY_ESCAPE && collectionTag.isOpen()) { collectionTag.close();return true; }
        if(e.key()==GLFW.GLFW_KEY_TAB && !busy()) { openBag();return true; }
        if(e.key()==GLFW.GLFW_KEY_ESCAPE && (dragging || erasing)) { if(dragging) returnTool(toolX,toolY); erasing=false; return true; }
        if(busy()) return true;
        if(e.key()==GLFW.GLFW_KEY_E) { erasing=!erasing; dragging=false; clickHeld=false; resizing=false; bagPickup=false; toolX=minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()); toolY=minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()); return true; }
        if(e.key()==GLFW.GLFW_KEY_N) { edit(()->turnTo(session.album().addCard(),1)); return true; }
        if(e.key()==GLFW.GLFW_KEY_LEFT_BRACKET || e.key()==GLFW.GLFW_KEY_RIGHT_BRACKET) { edit(()->turnTo(session.album().select(e.key()==GLFW.GLFW_KEY_LEFT_BRACKET?-1:1),e.key()==GLFW.GLFW_KEY_LEFT_BRACKET?-1:1)); return true; }
        if(e.key()==GLFW.GLFW_KEY_F) { Util.getPlatform().openPath(exportDirectory()); return true; }
        if(e.key()==GLFW.GLFW_KEY_B) { edit(()->commit(session.album().replace(card().withBackground(null)))); return true; }
        return super.keyPressed(e);
    }
    @Override public void tick() {
        if(tool!=null && !available(tool)){tool=null;dragging=false;clickHeld=false;resizing=false;bagPickup=false;}
        if(pressing!=null && !available(pressing.stamp))pressing=null;
        returning.removeIf(r->!available(r.stamp));
        if(collectionTag!=null && collectionTag.sync(session.album().stamps()) && now()-lastCollectionSound>=220) {
            lastCollectionSound=now();minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(),1.6f,.2f));
        }
        if(cardTurn!=null && now()-cardTurn.start>=560) finishTurn();
        if(pressing!=null) {
            long elapsed=now()-pressing.start;
            if(elapsed>=120 && !pressing.committed) {
                var p=pressing;
                try {
                    commit(session.album().replace(card().stamp(p.stamp.key(),p.stamp.asset(),p.x,p.y,p.size,p.angle)));
                    pressing=p.markCommitted(); sound(true); say(tr("saved"));
                } catch(Exception e) { pressing=null; fail(e); }
            }
            if(elapsed>=380 && pressing!=null) {
                var p=pressing; revealShelfStamp(p.stamp);returning.add(returningTool(p.stamp,cardX+p.x*cardW,cardY+p.y*cardH,p.angle,p.size)); pressing=null;
            }
        }
        returning.removeIf(r->now()-r.start>=r.duration);
        if(requested!=null && !busy()) {
            session.album().stamps().stream().filter(s->s.key().equals(requested)).findFirst().ifPresent(s->{
                if(!requestedSelected) { tool=s; erasing=false; requestedSelected=true; }
            });
        }
        if(tool!=null && !busy() && !dragging) {
            String selectedKey=tool.key();
            session.album().stamps().stream().filter(s->s.key().equals(selectedKey)).findFirst().ifPresent(s->tool=s);
        }
        String note=SignMeUpBridge.notice();
        if(!note.isEmpty() && !note.equals(lastNotice)) { lastNotice=note; say(note); }
        if(packing && now()-sendAt>=2050) {
            packing=false; minecraft.setScreen(new SignatureScreen(this,session));
        }
        if(exporting) {
            if(exported!=null && flyAt==0 && now()-sendAt>=2050) flyAt=now();
            if(flyAt!=0 && now()-flyAt>1000) { exporting=false; releaseSendCanvas(); edit(()->turnTo(session.album().addCard(),1)); say(tr("exported")+": "+exported); sound(false); edit(()->Util.getPlatform().openPath(exported)); }
        }
    }
    private boolean requestedSelected;
    private boolean available(StampDefinition stamp) {
        if(isTicket(stamp)) {
            try{return dev.postmark.model.CollectionProgress.from(session.album().stamps()).unlocks(Integer.parseInt(stamp.key().substring("postmark:milestone/".length())));}
            catch(NumberFormatException e){return false;}
        }
        return session.album().stamps().stream().anyMatch(s->s.key().equals(stamp.key()));
    }
    private String lastNotice="";
    private void sound(boolean stamp) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(stamp?SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT:SoundEvents.UI_BUTTON_CLICK.value(),stamp?.8f:1.1f));
    }
    public Path exportDirectory() { return session.store().directory().resolve("exports"); }
    private void send() {
        if(busy()) return;
        dragging=false;
        packing=true; sendAt=now(); flyAt=0;
        sendCanvas=canvas; // Alias only during packing; removed() owns the single canvas release.
    }
    void sendSigned() {
        if(busy()) return;
        try { sendCanvas=upload(PostcardPainter.paintBack(card())); }
        catch(IOException e) { fail(e); return; }
        dragging=false; exporting=true; sendAt=now()-2050; flyAt=0; exported=null;
        Postcard snapshot=card();
        CompletableFuture.supplyAsync(()->{
            try { return dev.postmark.storage.PostcardExporter.export(session.store(),snapshot); }
            catch(Exception e) { throw new java.util.concurrent.CompletionException(e); }
        }).whenComplete((path,error)->minecraft.execute(()->{
            if(error==null) exported=path;
            else { exporting=false; releaseSendCanvas(); fail(new IOException(tr("export_failed"),error)); }
        }));
    }
    private void releaseSendCanvas() {
        if(sendCanvas!=null) { if(!sendCanvas.equals(canvas)) minecraft.getTextureManager().release(sendCanvas); sendCanvas=null; }
    }
    private static double ease(double x) { x=Math.clamp(x,0,1); return x*x*(3-2*x); }
    private void drawSend(GuiGraphicsExtractor g,int mouseX,int mouseY) {
        double elapsed=now()-sendAt;
        double slide=ease((elapsed-200)/1050), fold=ease((elapsed-1350)/500);
        double flight=flyAt==0?0:ease((now()-flyAt)/1000.0);
        double lift=packing?ease(elapsed/200):1;
        int finalW=(int)EnvelopeLayout.packedWidth(card().aspectRatio(),width,height);
        int w=Math.max(1,(int)(cornerW()*UI_SCALE+(finalW-cornerW()*UI_SCALE)*lift)),h=Math.max(1,(int)(w/card().aspectRatio()));
        int x=(int)(cornerX()*(1-lift)+(width-w)/2.0*lift)+(int)(flight*(width+w));
        int y=(int)(cornerY()*(1-lift)+((height-h)/2.0+25)*lift)-(int)(flight*height*.6);
        g.pose().pushMatrix(); g.pose().rotateAbout((float)(flight*-.12),x+w/2f,y+h/2f);
        if(exporting) {
            g.fill(x+4,y+5,x+w+4,y+h+5,0x3030271D);
            if(sendCanvas!=null) blit(g,sendCanvas,x,y,w,h);
            g.pose().popMatrix();
            String sendHint=mouseX>=x && mouseX<=x+w && mouseY>=y && mouseY<=y+h?"保存明信片和签名信封；成功后打开文件夹并换新卡":exported==null?"保存中":"已保存";
            HoverHint.draw(g,font,sendHint,width,height);
            return;
        }
        g.fill(x+5,y+h+3,x+w+7,y+h+9,0x153A372B);
        g.fill(x+3,y+6,x+w+3,y+h+5,0x253A372B);
        // The open flap swings through an edge-on position before covering the pocket.
        int flapHeight=h*2/3, flapTip=y+(int)((fold*2-1)*flapHeight);
        triangle(g,x,y,x+w,y,x+w/2,flapTip,fold<.5?0xFFC3B191:0xFFEADABD);
        g.fill(x,y,x+w,y+h,0xFFBDAC8C);
        g.fill(x+3,y+2,x+w-3,y+h-3,0xFFD4C4A4);
        int offset=(int)((1-slide)*h*.78);
        if(sendCanvas!=null) {
            double margin=Math.max(1,Math.min(w,h)*.065);
            double fitW=Math.max(1,Math.min(w-margin*2,(h-margin*2)*card().aspectRatio())),fitH=fitW/card().aspectRatio();
            int paperX=(int)(cardX*(1-lift)+(x+(w-fitW)/2)*lift);
            int paperY=(int)(cardY*(1-lift)+(y-offset+(h-fitH)/2)*lift);
            int paperW=(int)(cardW*(1-lift)+fitW*lift),paperH=(int)(cardH*(1-lift)+fitH*lift);
            // The paper remains inside the envelope for the entire fold. The opaque pocket
            // and flap cover it geometrically; finishing the slide never hides its texture.
            g.enableScissor(0,0,width,height);
            g.fill(paperX+2,paperY+3,paperX+paperW+2,paperY+paperH+3,0x233B3527);
            blit(g,sendCanvas,paperX,paperY,paperW,paperH);
            g.disableScissor();
        }
        // Paper passes behind the two side folds and front pocket.
        triangle(g,x,y+3,x+w/2,y+h*2/3,x,y+h,0xFFDECCAB);
        triangle(g,x+w,y+3,x+w,y+h,x+w/2,y+h*2/3,0xFFE6D5B4);
        triangle(g,x,y+h,x+w/2,y+h/3,x+w,y+h,0xFFF0E2C4);
        line(g,x,y+h,x+w/2,y+h/3,0xFFC6B590);
        line(g,x+w/2,y+h/3,x+w,y+h,0xFFD0BF9D);
        g.fill(x,y+h-2,x+w,y+h,0xFFC5B28D);
        if(fold>.5) {
            triangle(g,x,y+2,x+w,y+2,x+w/2,flapTip+3,0x243F382A);
            triangle(g,x,y,x+w,y,x+w/2,flapTip,0xFFEADABD);
            line(g,x,y,x+w/2,flapTip,0xFFF8EDDA);
            line(g,x+w/2,flapTip,x+w,y,0xFFC4AF89);
        }
        if(fold>=1) {
            int cx=x+w/2, cy=y+flapHeight-1;
            g.fill(cx-11,cy-7,cx+11,cy+8,0xFF853F32);
            g.fill(cx-8,cy-10,cx+8,cy+11,0xFF853F32);
            g.fill(cx-9,cy-6,cx+9,cy+7,0xFFAD5941);
            g.fill(cx-6,cy-8,cx+6,cy+9,0xFFAD5941);
            g.fill(cx-6,cy-7,cx+4,cy-5,0xFFD18963);
            g.text(font,Component.literal("P").withStyle(net.minecraft.ChatFormatting.BOLD),cx-3,cy-3,0xFFF1C994,false);
        }
        g.pose().popMatrix();

    }
    private static void triangle(GuiGraphicsExtractor g,int ax,int ay,int bx,int by,int cx,int cy,int color) {
        int min=Math.min(ay,Math.min(by,cy)),max=Math.max(ay,Math.max(by,cy));
        for(int y=min;y<=max;y++) {
            double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
            int[] xs={ax,bx,cx},ys={ay,by,cy};
            for(int i=0;i<3;i++) {
                int j=(i+1)%3;
                if(ys[i]==ys[j]) continue;
                if(y>=Math.min(ys[i],ys[j]) && y<=Math.max(ys[i],ys[j])) {
                    double v=xs[i]+(double)(y-ys[i])*(xs[j]-xs[i])/(ys[j]-ys[i]);
                    lo=Math.min(lo,v); hi=Math.max(hi,v);
                }
            }
            if(lo<=hi) g.fill((int)lo,y,(int)hi+1,y+1,color);
        }
    }
    private static void line(GuiGraphicsExtractor g,int x,int y,int tx,int ty,int color) {
        int steps=Math.max(Math.abs(tx-x),Math.abs(ty-y));
        for(int i=0;i<=steps;i++) { double t=steps==0?0:(double)i/steps; int px=(int)Math.round(x+(tx-x)*t),py=(int)Math.round(y+(ty-y)*t); g.fill(px,py,px+1,py+1,color); }
    }
    @Override public void onFilesDrop(List<Path> paths) {
        if(busy() || bag.isOpen() || paths.isEmpty()) return;
        edit(()->{
            BufferedImage image=ImageFiles.readPhoto(paths.getFirst());
            Identifier cropped=null,original=null;
            try {
                var originalCard=card().withBackground("photo-preview",image.getWidth(),image.getHeight(),false);
                PostcardPainter.Images source=name->name.equals("photo-preview")?image:this.image(name);
                cropped=upload(PostcardPainter.paint(card().withBackground("photo-preview"),source));
                original=upload(PostcardPainter.paint(originalCard,source));
                photoChoice=new PhotoImportChoice(image,cropped,original,originalCard.aspectRatio());
                if(dragging) returnTool(toolX,toolY);
                erasing=false;collectionTag.close();
            } catch(Exception e) {
                if(cropped!=null) minecraft.getTextureManager().release(cropped);
                if(original!=null) minecraft.getTextureManager().release(original);
                throw e;
            }
        });
    }
    public boolean choosingPhoto() { return photoChoice!=null; }
    private void cancelPhoto() {
        if(photoChoice!=null) {
            minecraft.getTextureManager().release(photoChoice.cropped());
            minecraft.getTextureManager().release(photoChoice.original());
            photoChoice=null;
        }
    }
    @Override public void onClose() {
        if(photoChoice!=null) { cancelPhoto();return; }
        if(busy()) return;
        minecraft.setScreen(parent);
    }
    @Override public void removed() {
        cancelPhoto();releaseLandingPreview();
        dragging=false; erasing=false; pressing=null; returning.clear(); resizing=false; finishTurn(); releaseSendCanvas();
        if(canvas!=null) { minecraft.getTextureManager().release(canvas); canvas=null; }
        for(var id:textures.values()) minecraft.getTextureManager().release(id);
        textures.clear(); images.clear();
    }
    @Override public boolean isPauseScreen() { return false; }
}
