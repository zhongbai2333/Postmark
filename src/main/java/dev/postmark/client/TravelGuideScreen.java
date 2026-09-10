package dev.postmark.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.postmark.Postmark;
import dev.postmark.compat.GuideBridge;
import dev.postmark.model.*;
import dev.postmark.render.StampArtwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** A handmade travel collage: every pasted scrap shares its drawing and input transform. */
public final class TravelGuideScreen extends Screen {
    private final Screen parent;
    private final ClientSession session;
    private final Map<String,Identifier> art=new LinkedHashMap<>();
    private final Set<String> pending=new HashSet<>(),failed=new HashSet<>();
    private TravelJournal journal=TravelJournal.empty();
    private List<GuideVenue> venues=List.of();
    private int detailPage,serial,bx,by,bw,bh;
    private final GuideViewport view=new GuideViewport();
    private GuideCanvasLayout canvas=GuideCanvasLayout.scatter(List.of());
    private List<UUID> layoutIds=List.of();
    private List<Tile> canvasTiles=List.of();
    private final Map<UUID,Float> enlargement=new HashMap<>();
    private UUID hovering;
    private boolean cameraReady,dragging,panArmed;
    private double pressX,pressY,lastDragX,lastDragY;
    private long lastFrame;
    private UUID detail;
    private Hit pressed;
    private String error="";
    private boolean live;
    private long opened;
    private static final int INK=0xFF443B2D,PAPER=0xFFE4D3A5;
    private record Hit(String action,UUID venue,String stamp) {}
    private record Tile(GuideVenue venue,int x,int y,int size,int variant,float angle) {
        double[] screen(double px,double py) {
            double cx=x+size/2.0,cy=y+size/2.0,dx=px-cx,dy=py-cy;
            return new double[]{cx+dx*Math.cos(angle)-dy*Math.sin(angle),cy+dx*Math.sin(angle)+dy*Math.cos(angle)};
        }
        double[] local(double px,double py) {
            double cx=x+size/2.0,cy=y+size/2.0,dx=px-cx,dy=py-cy;
            return new double[]{cx+dx*Math.cos(angle)+dy*Math.sin(angle),cy-dx*Math.sin(angle)+dy*Math.cos(angle)};
        }
    }
    public TravelGuideScreen(Screen parent,ClientSession session) {super(Component.literal("漫游志"));this.parent=parent;this.session=session;}
    public static void show(Screen parent) {
        try {Minecraft.getInstance().setScreen(new TravelGuideScreen(parent,ClientSession.get()));}
        catch(Exception e) {Postmark.LOGGER.warn("Cannot open travel guide",e);}
    }
    private static long now() {return System.nanoTime()/1_000_000;}
    @Override protected void init() {boolean first=!cameraReady;live=true;opened=now();lastFrame=opened;sync();layout();if(first)view.enter();}
    private void layout() {
        bx=16;by=55;bw=Math.max(180,width-72);bh=Math.max(100,height-83);
        var ids=venues.stream().map(GuideVenue::id).toList();boolean changed=!ids.equals(layoutIds);
        if(changed){layoutIds=ids;canvas=GuideCanvasLayout.scatter(ids);}
        if(changed || canvasTiles.size()!=venues.size()) {
            var tiles=new ArrayList<Tile>();for(var n:canvas.nodes())tiles.add(new Tile(venues.get(n.index()),n.x(),n.y(),n.size(),n.variant(),n.angle()));canvasTiles=List.copyOf(tiles);
        }else if(!canvasTiles.isEmpty()) {
            // Metadata can change while the camera and paper positions remain stable.
            var first=canvasTiles.getFirst();if(first.venue!=venues.getFirst()) {
                var tiles=new ArrayList<Tile>();for(var n:canvas.nodes())tiles.add(new Tile(venues.get(n.index()),n.x(),n.y(),n.size(),n.variant(),n.angle()));canvasTiles=List.copyOf(tiles);
            }
        }
        view.resize(bw,bh,canvas.width(),canvas.height(),!cameraReady);if(!cameraReady&&!venues.isEmpty())view.readable();cameraReady=true;
    }
    private void sync() {
        venues=GuideBridge.venues();
        try {journal=session.journal();}catch(Exception e){error="检索记录读取失败，原文件已保留";}
    }
    public UUID detailVenue() {return detail;}
    public int canvasVenueCount(){return canvasTiles.size();}
    public double zoomLevel(){return view.zoom();}
    public double hoverScale(UUID id){return 1+enlargement.getOrDefault(id,0f)*.17;}
    private boolean fromInventory(){return parent instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;}
    public double[] stampDeskCenter(){return new double[]{width-28,by+19};}
    public double[] fitCenter(){return new double[]{width-28,height-84};}
    private Tile tile(UUID id){return canvasTiles.stream().filter(t->t.venue.id().equals(id)).findFirst().orElseThrow();}
    private double[] toScreen(Tile t,double x,double y) {
        var p=t.screen(x,y);double cx=t.x+t.size/2.0,cy=t.y+t.size/2.0,k=hoverScale(t.venue.id());
        return new double[]{bx+view.x()+(cx+(p[0]-cx)*k)*view.zoom(),by+view.y()+(cy+(p[1]-cy)*k)*view.zoom()};
    }
    public double[] iconCenter(UUID id){var t=tile(id);return toScreen(t,t.x+t.size/2.0,t.y+t.size/2.0);}
    public double[] cornerCenter(UUID id,boolean expert){var t=tile(id);return toScreen(t,expert?t.x+t.size:t.x,t.y);}
    public double[] captionCenter(UUID id){var t=tile(id);return toScreen(t,t.x+t.size/2.0,t.y+t.size+13);}
    private List<Tile> tiles(){return canvasTiles;}
    private boolean visible(Tile t){var p=toScreen(t,t.x+t.size/2.0,t.y+t.size/2.0);double r=(t.size+55)*view.zoom();return p[0]+r>bx&&p[0]-r<bx+bw&&p[1]+r>by&&p[1]-r<by+bh;}
    private List<Tile> paintOrder() {
        var list=new ArrayList<Tile>();Tile top=null;
        for(var t:tiles())if(visible(t)){if(t.venue.id().equals(hovering))top=t;else list.add(t);}
        if(top!=null)list.add(top);return list;
    }
    private static void tape(GuiGraphicsExtractor g,int x,int y,int w,int color) {
        g.fill(x+1,y,x+w-1,y+9,color);g.fill(x,y+2,x+w,y+7,color);
        g.fill(x+3,y+1,x+w-3,y+2,0x40FFF6CE);
        for(int i=4;i<w-2;i+=6)g.fill(x+i,y+4,x+i+1,y+8,0x20796845);
    }
    private void scrap(GuiGraphicsExtractor g,Tile t,boolean hot) {
        int x=t.x,y=t.y,s=t.size,v=t.variant,paper=v==1?0xFFEBDCA9:v==2?0xFFD6DDC3:0xFFF2E5C7;
        g.fill(x-7,y-5,x+s+11,y+s+28,0x383A3021);
        g.fill(x-10,y-8,x+s+8,y+s+22,paper);
        g.fill(x-8,y-10,x+s+6,y-8,paper);g.fill(x-8,y+s+22,x+s+5,y+s+24,paper);
        for(int i=0;i<s+12;i+=9)g.fill(x-7+i,y+s+23,x-3+i,y+s+25,paper);
        g.fill(x-8,y-8,x+s+6,y-7,0xFFFFF1D1);
        g.fill(x-2,y-2,x+s+2,y+s+2,0xFFB4A581);picture(g,t.venue,x,y,s);
        tape(g,x+s/2-14,y-14,28,v%2==0?0xCCBCBA87:0xCCB7C4AC);
        String name=font.plainSubstrByWidth(t.venue.name(),s+10);
        if(!name.equals(t.venue.name()))name=font.plainSubstrByWidth(t.venue.name(),s+1)+"…";
        g.text(font,Component.literal(name),x+(s-font.width(name))/2,y+s+9,INK,false);
        if(v==0||v==3)g.fill(x+5,y+s+20,x+s-6,y+s+21,0x8076674B);
        if(hot){g.outline(x-3,y-3,s+6,s+6,0xFF64553B);g.fill(x+s-11,y+s-9,x+s+1,y+s+3,PAPER);arrow(g,1,x+s-5,y+s-3,true);}
    }
    private void paper(GuiGraphicsExtractor g) {
        int w=(int)canvas.width(),h=(int)canvas.height();
        g.fill(5,7,w+5,h+7,0x50382F20);g.fill(0,0,w,h,PAPER);g.fill(2,2,w-2,4,0xFFF4E5B9);
        g.fill(0,h-3,w,h,0xFFBDA276);
        int left=Math.max(0,(int)Math.floor(view.worldX(0)/140)-1),top=Math.max(0,(int)Math.floor(view.worldY(0)/140)-1);
        int right=Math.min(Math.max(0,(w-110)/140+1),(int)Math.ceil(view.worldX(bw)/140)+1),bottom=Math.min(Math.max(0,(h-110)/140+1),(int)Math.ceil(view.worldY(bh)/140)+1);
        for(int iy=top;iy<bottom;iy++)for(int ix=left;ix<right;ix++) {
            int x=12+ix*140,y=12+iy*140,kind=Math.floorMod(ix+iy*3,4),color=0x227E987B;
            if(kind==0){ // Terraced mountains, like a faint map printed on the paper.
                for(int i=0;i<4;i++){int px=x+i*10,py=y+36-i*7;g.fill(px,py,px+11,py+2,color);g.fill(px+9,py-6,px+11,py+2,color);}
                for(int i=0;i<3;i++)g.fill(x+41+i*8,y+16+i*7,x+50+i*8,y+18+i*7,color);
            }else if(kind==1){
                for(int i=0;i<5;i++){int py=y+i*9,px=x+(i%2)*7;g.fill(px,py,px+42,py+2,0x207D9C9D);g.fill(px+41,py,px+43,py+5,0x207D9C9D);}
            }else if(kind==2){
                g.fill(x+22,y+12,x+25,y+50,color);
                for(int i=0;i<3;i++){int py=y+i*12;g.fill(x+7+i*4,py+9,x+39-i*3,py+16,color);g.fill(x+13+i*4,py+4,x+32-i*2,py+9,color);}
            }else {
                for(int i=0;i<38;i+=6){g.fill(x+i,y+4,x+i+2,y+6,0x228F7954);g.fill(x+38,y+i/2+5,x+40,y+i/2+7,0x228F7954);}
                g.fill(x+36,y+28,x+43,y+30,0x228F7954);g.fill(x+39,y+25,x+41,y+33,0x228F7954);
            }
            g.fill(x+78,y+63,x+82,y+64,0x168A714C);g.fill(x+95,y+91,x+97,y+93,0x168A714C);
        }
    }
    private void chrome(GuiGraphicsExtractor g) {
        int y=14-(int)(6*Math.exp(-(now()-opened)/90.0));
        g.fill(18,y+3,145,y+33,0x50382F20);g.fill(15,y,142,y+30,0xFFF0E3BD);
        tape(g,12,y-3,24,0xC0B1BD9B);tape(g,121,y+23,24,0xC0B1BD9B);
        g.pose().pushMatrix();g.pose().translate(28,y+6);g.pose().scale(2,2);g.text(font,Component.literal("漫游志"),0,0,INK,false);g.pose().popMatrix();
        g.text(font,Component.literal("走走 · 逛逛 · 盖盖章"),159,y+13,0xFFE8DFBE,false);
        int x=width-28;
        g.fill(x-13,15,x+13,41,0xFFAD7859);DeskControls.draw(g,DeskControls.Kind.CLOSE,x,28,true);
        if(fromInventory())drawStampEntry(g,x,by+19);
        for(int cy:new int[]{height-120,height-84,height-48}){g.fill(x-14,cy-14,x+14,cy+14,0xFFF0E3BD);g.fill(x-12,cy+12,x+14,cy+15,0xFFAD936A);}
        g.fill(x-6,height-121,x+7,height-119,INK);g.fill(x-1,height-126,x+1,height-113,INK);
        int cy=height-84;for(int sx:new int[]{-1,1})for(int sy:new int[]{-1,1}){int px=x+sx*7,py=cy+sy*7;g.fill(px-(sx<0?0:4),py,px+(sx<0?5:1),py+1,INK);g.fill(px,py-(sy<0?0:4),px+1,py+(sy<0?5:1),INK);}
        g.fill(x-6,height-49,x+7,height-47,INK);
        g.centeredText(font,Component.literal(Math.round(view.zoom()*100)+"%"),x,height-22,0xFFE8DFBE);
    }
    private void drawStampEntry(GuiGraphicsExtractor g,int x,int y) {
        g.fill(x-17,y-25,x+17,y+24,0xFFE8D7AD);tape(g,x-12,y-28,24,0xC0B1BD9B);
        g.fill(x-13,y+6,x+14,y+16,0xFF503D2E);g.fill(x-11,y+5,x+12,y+12,0xFFC29560);
        g.fill(x-10,y+15,x+11,y+18,0xFF343A32);g.fill(x-4,y-7,x+4,y+6,0xFF765037);
        g.fill(x-9,y-17,x+9,y-6,0xFF5C402E);g.fill(x-7,y-19,x+7,y-4,0xFF9C6946);g.fill(x-5,y-17,x+5,y-14,0xFFD3AC78);
    }
    private List<TravelJournal.KnownStamp> observed(UUID id) {return journal.stamps(id,session.album().stamps());}
    private List<TravelJournal.KnownStamp> stamps(UUID id) {return StampCatalog.bundled().merge(id,observed(id));}
    private boolean complete(UUID id) {return StampCatalog.bundled().complete(id,observed(id));}
    private TravelJournal.KnownStamp stamp(UUID id,String kind) {return stamps(id).stream().filter(s->s.id().equals(kind)).findFirst().orElse(null);}
    private void picture(GuiGraphicsExtractor g,GuideVenue venue,int x,int y,int size) {
        Identifier id=Identifier.parse(venue.icon());
        if(minecraft.getResourceManager().getResource(id).isPresent())g.blit(id,x,y,x+size,y+size,0f,1f,0f,1f);
        else {g.outline(x,y,size,size,0xFFB6B395);g.centeredText(font,Component.literal("?"),x+size/2,y+size/2-4,0xFF94977B);}
    }
    private Identifier artwork(TravelJournal.KnownStamp stamp) {
        if(stamp.asset()==null && stamp.item()==null)return null;
        String key=(stamp.id().equals("expert")?"gold/":"wood/")+(stamp.asset()!=null?"asset/"+stamp.asset():"item/"+stamp.item());
        if(art.containsKey(key))return art.get(key);
        if(pending.contains(key)||failed.contains(key))return null;
        pending.add(key);
        CompletableFuture<java.awt.image.BufferedImage> future;
        try {future=stamp.asset()!=null?CompletableFuture.completedFuture(session.store().image(stamp.asset())):StampArtwork.capture(stamp.item());}
        catch(Exception e){future=CompletableFuture.failedFuture(e);}
        future=future.thenApply(image->dev.postmark.render.StampToolPainter.paint(new StampDefinition("guide/"+stamp.id(),stamp.id(),"preview",false),image));
        future.whenComplete((image,failure)->Minecraft.getInstance().execute(()->{
            pending.remove(key);if(!live || Minecraft.getInstance().screen!=this)return;
            if(failure!=null){failed.add(key);Postmark.LOGGER.debug("Guide stamp preview unavailable: {}",key,failure);return;}
            var pixels=new NativeImage(image.getWidth(),image.getHeight(),false);
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)pixels.setPixel(x,y,image.getRGB(x,y));
            var id=Identifier.fromNamespaceAndPath("postmark","guide/"+System.identityHashCode(this)+"/"+serial++);
            if(art.size()>=96){var oldest=art.entrySet().iterator();var entry=oldest.next();minecraft.getTextureManager().release(entry.getValue());oldest.remove();}
            minecraft.getTextureManager().register(id,new DynamicTexture(()->"Guide stamp preview",pixels));art.put(key,id);
        }));
        return art.get(key);
    }
    private void stampPicture(GuiGraphicsExtractor g,TravelJournal.KnownStamp stamp,int cx,int cy,int size) {
        var texture=artwork(stamp);float scale=size/64f;
        g.pose().pushMatrix();g.pose().translate(cx,cy);g.pose().scale(scale,scale);
        if(texture!=null) {
            int tint=(stamp.owned()?0xFF000000:0x66000000)|0xFFFFFF;
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,texture,-40,-64,0f,0f,80,104,1,1,1,1,tint);
        }else {
            int color=stamp.id().equals("expert")?0xFFB8944A:0xFF8A684B;if(!stamp.owned())color=(color&0xFFFFFF)|0x66000000;
            g.fill(-31,-24,31,28,color);g.fill(-8,-42,8,-24,color);g.fill(-17,-58,17,-41,color);
            if(stamp.asset()==null && stamp.item()==null) {
                g.pose().pushMatrix();g.pose().scale(3,3);g.centeredText(font,Component.literal("?"),0,-3,0xFF8C8063);g.pose().popMatrix();
            }
        }
        g.pose().popMatrix();
    }
    private static void footprint(GuiGraphicsExtractor g,int x,int y) {
        g.pose().pushMatrix();g.pose().rotateAbout(.28f,x,y);
        String[] sole={"01110","11111","11111","11111","01110","01100","01100","00110","00110","00110"};
        for(int i=0;i<2;i++)for(int row=0;row<sole.length;row++)for(int col=0;col<5;col++)if(sole[row].charAt(col)=='1') {
            int px=x+i*8+col,py=y+i*4+row;g.fill(px,py,px+1,py+1,0xBC77745A);
        }
        g.pose().popMatrix();
    }
    private static void completeMark(GuiGraphicsExtractor g,int x,int y,int size) {
        int r=Math.max(10,size/3);g.pose().pushMatrix();g.pose().rotateAbout(-.19f,x,y);
        for(int dy=-r;dy<=r;dy++)for(int dx=-r;dx<=r;dx++){int d=dx*dx+dy*dy;if(d<=r*r && d>=(r-2)*(r-2))g.fill(x+dx,y+dy,x+dx+1,y+dy+1,0xCF52704B);}
        for(int i=0;i<=r/2;i++)g.fill(x-r/2+i,y+i*2/3,x-r/2+i+2,y+i*2/3+2,0xDD52704B);
        for(int i=0;i<=r*2/3;i++)g.fill(x+i,y+r/3-i,x+i+2,y+r/3-i+2,0xDD52704B);
        g.pose().popMatrix();
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float t) {if(minecraft.level==null)extractPanorama(g,t);extractBlurredBackground(g);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float t) {
        layout();long time=now();double dt=(time-lastFrame)/1000.0;lastFrame=time;view.step(dt);
        Hit hover=dragging||detail!=null?null:hit(mx,my);hovering=hover==null?null:hover.venue;
        float amount=(float)(1-Math.exp(-Math.clamp(dt,0,.1)/.075));
        for(var tile:tiles()){UUID id=tile.venue.id();float v=enlargement.getOrDefault(id,0f),target=id.equals(hovering)?1:0;enlargement.put(id,v+(target-v)*amount);}
        g.fill(0,0,width,height,0xAA353E34);g.fill(bx-3,by-3,bx+bw+3,by+bh+3,0x403D4336);
        g.enableScissor(bx,by,bx+bw,by+bh);
        g.pose().pushMatrix();g.pose().translate((float)(bx+view.x()),(float)(by+view.y()));g.pose().scale((float)view.zoom(),(float)view.zoom());
        paper(g);
        for(var tile:paintOrder()) {
            boolean hot=tile.venue.id().equals(hovering);float cx=tile.x+tile.size/2f,cy=tile.y+tile.size/2f,k=(float)hoverScale(tile.venue.id());
            g.pose().pushMatrix();g.pose().translate(cx,cy);g.pose().scale(k,k);g.pose().translate(-cx,-cy);g.pose().rotateAbout(tile.angle,cx,cy);
            scrap(g,tile,hot);
            var known=stamps(tile.venue.id());
            if(complete(tile.venue.id())){g.fill(tile.x,tile.y,tile.x+tile.size,tile.y+tile.size,0x50E4D3A5);completeMark(g,tile.x+tile.size/2,tile.y+tile.size/2,tile.size);}
            else if(journal.entry(tile.venue.id()).searched())footprint(g,tile.x+tile.size-12,tile.y+tile.size-13);
            for(String kind:List.of("visitor","expert")){var s=stamp(tile.venue.id(),kind);if(s!=null)stampPicture(g,s,kind.equals("visitor")?tile.x:tile.x+tile.size,tile.y,24);}
            long extra=known.stream().filter(s->!s.id().equals("visitor")&&!s.id().equals("expert")).count();
            if(extra>0){g.fill(tile.x-8,tile.y+tile.size-10,tile.x+11,tile.y+tile.size+2,0xFFDFC995);g.text(font,Component.literal("+"+extra),tile.x-6,tile.y+tile.size-8,INK,false);}
            g.pose().popMatrix();
        }
        g.pose().popMatrix();g.disableScissor();
        if(venues.isEmpty())g.centeredText(font,Component.literal(GuideBridge.available()?"等待展馆目录…":"当前世界没有 SMU 展馆"),bx+bw/2,by+bh/2,0xFF7E8467);
        chrome(g);
        if(detail!=null)drawDetail(g,mx,my);
        String hint=hint(hit(mx,my));if(!error.isEmpty())hint=error;else if(hint.isBlank())hint=GuideBridge.status();
        HoverHint.draw(g,font,hint,width,height);
    }
    private static void arrow(GuiGraphicsExtractor g,int direction,int x,int y,boolean enabled) {for(int r=-2;r<=2;r++){int dx=(Math.abs(r)-1)*2*-direction;g.fill(x+dx,y+r*2,x+dx+2,y+r*2+2,enabled?0xFF8A9674:0x5598997B);}}
    private int dx(){return (width-Math.min(360,width-40))/2;}private int dy(){return (height-Math.min(290,height-38))/2;}
    private int dw(){return Math.min(360,width-40);}private int dh(){return Math.min(290,height-38);}
    private int detailRows(){return Math.max(1,(dh()-106)/52);}private int detailCapacity(){return detailRows()*4;}
    private void drawDetail(GuiGraphicsExtractor g,int mx,int my) {
        var venue=venues.stream().filter(v->v.id().equals(detail)).findFirst().orElse(null);if(venue==null){detail=null;return;}
        int x=dx(),y=dy(),w=dw(),h=dh();g.fill(0,0,width,height,0x66303A2A);g.fill(x+3,y+4,x+w+3,y+h+4,0x44302B1F);g.fill(x,y,x+w,y+h,0xFFF6ECD0);
        g.text(font,Component.literal(font.plainSubstrByWidth(venue.name(),w-50)),x+14,y+12,INK,false);DeskControls.draw(g,DeskControls.Kind.CLOSE,x+w-15,y+15,true);
        String desc=venue.description();if(desc.equals("@unset"))desc="";
        g.text(font,Component.literal(font.plainSubstrByWidth(desc,w-28)),x+14,y+30,0xFF8B866A,false);
        var known=stamps(detail);var preset=StampCatalog.bundled().entry(detail);
        String note=complete(detail)?(preset==null?"普通与大师均已收集":"清单内已集齐"):journal.entry(detail).searched()?"附近已检索":"尚未检索";
        if(preset!=null)note+=" · 玩家清单 "+StampCatalog.bundled().date();
        g.text(font,Component.literal(font.plainSubstrByWidth(note,w-28)),x+14,y+46,0xFF7A8164,false);
        detailPage=Math.min(detailPage,Math.max(0,(known.size()-1)/detailCapacity()));
        for(int i=0;i<detailCapacity() && detailPage*detailCapacity()+i<known.size();i++) {
            var s=known.get(detailPage*detailCapacity()+i);int cx=x+20+(w-40)*(i%4)/4+(w-40)/8,cy=y+84+(i/4)*52;
            stampPicture(g,s,cx,cy,24);String label=s.id().equals("visitor")?"普通":s.id().equals("expert")?"大师":s.id();
            g.centeredText(font,Component.literal(font.plainSubstrByWidth(label,(w-40)/4-4)),cx,cy+20,s.owned()?INK:0xFF9F967A);
        }
        if(known.isEmpty())g.centeredText(font,Component.literal(journal.entry(detail).searched()?"尚未发现印章":"等待探索"),x+w/2,y+80,0xFF9B967B);
        if(known.size()>detailCapacity()){arrow(g,-1,x+18,y+h-18,detailPage>0);arrow(g,1,x+w-18,y+h-18,(detailPage+1)*detailCapacity()<known.size());}
        g.fill(x+w/2-45,y+h-28,x+w/2+45,y+h-7,venue.canTeleport()?0xFF6A7C56:0xFFB4B096);
        g.centeredText(font,Component.literal(venue.canTeleport()?"前往展馆":"未设置传送点"),x+w/2,y+h-22,0xFFF7EED6);
    }
    private Hit hit(double x,double y) {
        if(detail!=null) {
            int dx=dx(),dy=dy(),w=dw(),h=dh();
            if(DeskControls.hit(x,y,dx+w-15,dy+15))return new Hit("dismiss",detail,null);
            if(Math.abs(x-(dx+w/2))<=45 && y>=dy+h-28 && y<=dy+h-7)return new Hit("travel",detail,null);
            if(DeskControls.hit(x,y,dx+18,dy+h-18))return new Hit("detailPrev",detail,null);
            if(DeskControls.hit(x,y,dx+w-18,dy+h-18))return new Hit("detailNext",detail,null);
            var known=stamps(detail);
            for(int i=0;i<detailCapacity() && detailPage*detailCapacity()+i<known.size();i++){int cx=dx+20+(w-40)*(i%4)/4+(w-40)/8,cy=dy+84+(i/4)*52;if(Math.abs(x-cx)<=17&&y>=cy-25&&y<=cy+18)return new Hit("stamp",detail,known.get(detailPage*detailCapacity()+i).id());}
            return null;
        }
        if(DeskControls.hit(x,y,width-28,28))return new Hit("close",null,null);
        if(fromInventory() && Math.abs(x-(width-28))<=18 && Math.abs(y-(by+19))<=25)return new Hit("desk",null,null);
        if(Math.abs(x-(width-28))<=15){if(Math.abs(y-(height-120))<=15)return new Hit("zoomIn",null,null);if(Math.abs(y-(height-84))<=15)return new Hit("fit",null,null);if(Math.abs(y-(height-48))<=15)return new Hit("zoomOut",null,null);}
        if(!onCanvas(x,y))return null;
        double wx=view.worldX(x-bx),wy=view.worldY(y-by);
        for(var t:paintOrder().reversed()) {
            double cx=t.x+t.size/2.0,cy=t.y+t.size/2.0,k=hoverScale(t.venue.id());
            var local=t.local(cx+(wx-cx)/k,cy+(wy-cy)/k);double tx=local[0],ty=local[1];
            for(String kind:List.of("visitor","expert"))if(stamp(t.venue.id(),kind)!=null && Math.abs(tx-(kind.equals("visitor")?t.x:t.x+t.size))<=17&&ty>=t.y-25&&ty<=t.y+17)return new Hit("stamp",t.venue.id(),kind);
            boolean extra=stamps(t.venue.id()).stream().anyMatch(s->!s.id().equals("visitor")&&!s.id().equals("expert"));
            if(extra&&tx>=t.x-9&&tx<=t.x+13&&ty>=t.y+t.size-11&&ty<=t.y+t.size+3)return new Hit("details",t.venue.id(),null);
            if(tx>=t.x&&tx<=t.x+t.size&&ty>=t.y&&ty<=t.y+t.size)return new Hit("travel",t.venue.id(),null);
            if(tx>=t.x-10&&tx<=t.x+t.size+8&&ty>=t.y+t.size+5&&ty<=t.y+t.size+25)return new Hit("details",t.venue.id(),null);
        }
        return null;
    }
    private String hint(Hit hit) {
        if(hit==null)return detail==null?"拖动纸面 · 滚轮缩放":"";
        var venue=venues.stream().filter(v->v.id().equals(hit.venue)).findFirst().orElse(null);
        return switch(hit.action){
            case "travel" -> venue!=null?(venue.canTeleport()?"前往 "+venue.name():"这个展馆尚未设置传送点"):"";
            case "details" -> "查看展馆介绍";
            case "stamp" -> {var s=stamp(hit.venue,hit.stamp);yield s==null?"":(s.id().equals("visitor")?"普通章":s.id().equals("expert")?"大师章":s.id())+(s.owned()?" · 点击拿去盖印":s.item()==null?" · 玩家清单，尚未实地发现":" · 已发现，尚未获得");}
            case "close" -> "收起漫游志";case "dismiss" -> "收起介绍";case "detailPrev" -> "上一页";case "detailNext" -> "下一页";case "zoomIn" -> "放大";case "zoomOut" -> "缩小";case "fit" -> "查看全部展馆";case "desk" -> "打开盖章工作台";default -> "";
        };
    }
    private boolean onCanvas(double x,double y){return x>=bx&&x<bx+bw&&y>=by&&y<by+bh;}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice) {
        dragging=false;panArmed=false;pressed=null;
        if(now()-opened<300)return true;
        pressX=lastDragX=e.x();pressY=lastDragY=e.y();
        panArmed=detail==null && onCanvas(e.x(),e.y()) && (e.button()==0||e.button()==2);
        if(e.button()==0)pressed=hit(e.x(),e.y());return true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy) {
        if(!panArmed)return true;
        if(!dragging && Math.hypot(e.x()-pressX,e.y()-pressY)>4){dragging=true;pressed=null;}
        if(dragging){view.pan(e.x()-lastDragX,e.y()-lastDragY);lastDragX=e.x();lastDragY=e.y();}
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) {
        var target=pressed;pressed=null;boolean moved=dragging||Math.hypot(e.x()-pressX,e.y()-pressY)>4;dragging=false;panArmed=false;
        if(moved||e.button()!=0||target==null||!target.equals(hit(e.x(),e.y())))return true;
        activate(target);return true;
    }
    private void activate(Hit target) {
        switch(target.action) {
            case "close" -> onClose();case "dismiss" -> detail=null;
            case "zoomIn" -> {pressed=null;view.zoomAt(bw/2.0,bh/2.0,1.3);}case "zoomOut" -> {pressed=null;view.zoomAt(bw/2.0,bh/2.0,1/1.3);}
            case "fit" -> {pressed=null;view.fit(false);}case "desk" -> PostcardScreen.show(this,null);
            case "detailNext" -> detailPage=Math.min(Math.max(0,(stamps(detail).size()-1)/detailCapacity()),detailPage+1);case "detailPrev" -> detailPage=Math.max(0,detailPage-1);
            case "details" -> {detail=target.venue;detailPage=0;}
            case "travel" -> {if(GuideBridge.teleport(target.venue))minecraft.setScreen(null);else error=GuideBridge.status();}
            case "stamp" -> {
                var s=stamp(target.venue,target.stamp);
                if(s!=null && s.owned()) {PostcardScreen.show(parent,target.venue+"/"+target.stamp);if(minecraft.screen instanceof PostcardScreen desk)desk.pickUpCollected(target.venue+"/"+target.stamp);}
                else {detail=target.venue;detailPage=0;}
            }
        }
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        pressed=null;
        if(dy!=0){if(detail!=null)activate(new Hit(dy<0?"detailNext":"detailPrev",detail,null));else if(onCanvas(x,y))view.zoomAt(x-bx,y-by,Math.pow(1.16,dy));}
        return true;
    }
    @Override public boolean keyPressed(KeyEvent e) {if(e.key()==256){onClose();return true;}return super.keyPressed(e);}
    @Override public void tick() {sync();}
    @Override public void onClose() {if(detail!=null){detail=null;return;}minecraft.setScreen(parent);}
    @Override public void removed() {live=false;for(var id:art.values())minecraft.getTextureManager().release(id);art.clear();pending.clear();}
    @Override public boolean isPauseScreen() {return false;}
}
