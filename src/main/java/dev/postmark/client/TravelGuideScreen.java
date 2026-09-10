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
    private final Map<String,Identifier> art=new LinkedHashMap<>(128,.75f,true);
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
    private boolean cameraReady,restoredCamera,dragging,panArmed;
    private double pressX,pressY,lastDragX,lastDragY;
    private long lastFrame;
    private UUID detail;
    private Hit pressed;
    private String error="";
    private boolean live;
    private int previewBudget,previewGeneration;
    private Identifier markers,patterns;
    private final Map<String,Optional<Identifier>> icons=new HashMap<>();
    private final Map<UUID,VenueState> states=new HashMap<>();
    private List<StampDefinition> stateOwned=List.of();
    private TravelJournal stateJournal;
    private List<GuideVenue> stateVenues;
    private List<GuideVenue> arrangedVenues;
    private record VenueState(List<TravelJournal.KnownStamp> stamps,boolean searched,boolean area,boolean complete,boolean inspection,
                              TravelJournal.KnownStamp visitor,TravelJournal.KnownStamp expert,long extra) {}
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
    @Override protected void init() {boolean first=!cameraReady;live=true;opened=now();lastFrame=opened;sync();layout();if(markers==null)markers=registerTexture("markers",dev.postmark.render.GuideMarkerPainter.paint());if(patterns==null)patterns=registerTexture("paper",dev.postmark.render.GuideMarkerPainter.paperPatterns());icons.clear();if(first&&!restoredCamera)view.enter();}
    private void layout() {
        bx=16;by=55;bw=Math.max(180,width-72);bh=Math.max(100,height-83);
        if(arrangedVenues!=venues) {
            var ids=venues.stream().map(GuideVenue::id).toList();
            if(!ids.equals(layoutIds)){layoutIds=ids;canvas=GuideCanvasLayout.scatter(ids);}
            var tiles=new ArrayList<Tile>();for(var n:canvas.nodes())tiles.add(new Tile(venues.get(n.index()),n.x(),n.y(),n.size(),n.variant(),n.angle()));canvasTiles=List.copyOf(tiles);
            arrangedVenues=venues;
        }
        view.resize(bw,bh,canvas.width(),canvas.height(),!cameraReady);
        if(!cameraReady&&!venues.isEmpty()) {
            view.readable();
            try {var saved=session.guideView();if(saved!=null){view.restore(saved);restoredCamera=true;}}
            catch(Exception e){Postmark.LOGGER.warn("Cannot restore travel guide view",e);}
            cameraReady=true;
        }
    }

    private void sync() {
        venues=GuideBridge.venues();
        try {journal=session.journal();}catch(Exception e){error="检索记录读取失败，原文件已保留";}
        var owned=session.album().stamps();
        if(stateJournal!=journal || stateOwned!=owned || stateVenues!=venues) {
            states.clear();var catalog=StampCatalog.bundled();
            for(var venue:venues) {
                var id=venue.id();var observed=journal.stamps(id,owned);var known=catalog.merge(id,observed);
                boolean searched=journal.entry(id).searched(),area=journal.areaSearched(venue);
                var visitor=known.stream().filter(stamp->stamp.id().equals("visitor")).findFirst().orElse(null);
                var expert=known.stream().filter(stamp->stamp.id().equals("expert")).findFirst().orElse(null);
                states.put(id,new VenueState(known,searched,area,catalog.complete(id,observed,area),catalog.needsInspection(id,searched,observed,area),visitor,expert,
                        known.stream().filter(stamp->!stamp.id().equals("visitor")&&!stamp.id().equals("expert")).count()));
            }
            stateJournal=journal;stateOwned=owned;stateVenues=venues;
        }
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
    public double[] captionEdge(UUID id){var t=tile(id);return toScreen(t,t.x+t.size+6,t.y+t.size+15);}
    public double[] inspectionCenter(UUID id){var t=tile(id);return toScreen(t,t.x+t.size/2.0,t.y-16);}
    public double[] footprintCenter(UUID id){var t=tile(id);return toScreen(t,t.x+t.size-12,t.y+t.size-16);}
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
        // Sparse motifs stay bounded in screen space even at the minimum overview zoom.
        int stride=1;while(140*stride*view.zoom()<45)stride*=2;int spacing=140*stride;
        int left=(int)Math.floor(view.worldX(0)/spacing)-1,top=(int)Math.floor(view.worldY(0)/spacing)-1;
        int right=(int)Math.ceil(view.worldX(bw)/spacing)+1,bottom=(int)Math.ceil(view.worldY(bh)/spacing)+1;
        for(int iy=top;iy<bottom;iy++)for(int ix=left;ix<right;ix++) {
            int x=ix*spacing,y=iy*spacing,kind=Math.floorMod(ix+iy*3,4);
            g.blit(patterns,x,y,x+140,y+140,kind/4f,(kind+1)/4f,0f,1f);
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
    private List<TravelJournal.KnownStamp> stamps(UUID id) {var state=states.get(id);return state==null?List.of():state.stamps;}
    private boolean areaSearched(UUID id) {var state=states.get(id);return state!=null&&state.area;}
    private boolean complete(UUID id) {var state=states.get(id);return state!=null&&state.complete;}
    public boolean needsInspection(UUID id) {var state=states.get(id);return state==null||state.inspection;}
    private TravelJournal.KnownStamp stamp(UUID id,String kind) {
        var state=states.get(id);if(state==null)return null;
        return kind.equals("visitor")?state.visitor:kind.equals("expert")?state.expert:state.stamps.stream().filter(s->s.id().equals(kind)).findFirst().orElse(null);
    }
    private void picture(GuiGraphicsExtractor g,GuideVenue venue,int x,int y,int size) {
        var icon=icons.computeIfAbsent(venue.icon(),source->{var id=Identifier.tryParse(source);return id!=null&&minecraft.getResourceManager().getResource(id).isPresent()?Optional.of(id):Optional.empty();});
        if(icon.isPresent())g.blit(icon.get(),x,y,x+size,y+size,0f,1f,0f,1f);
        else {g.outline(x,y,size,size,0xFFB6B395);g.centeredText(font,Component.literal("?"),x+size/2,y+size/2-4,0xFF94977B);}
    }
    private Identifier registerTexture(String kind,java.awt.image.BufferedImage image) {
        var pixels=new NativeImage(image.getWidth(),image.getHeight(),false);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)pixels.setPixel(x,y,image.getRGB(x,y));
        var id=Identifier.fromNamespaceAndPath("postmark","guide/"+System.identityHashCode(this)+"/"+kind+serial++);
        minecraft.getTextureManager().register(id,new DynamicTexture(()->"Guide "+kind,pixels));return id;
    }
    private void marker(GuiGraphicsExtractor g,int type,int x,int y) {
        g.pose().pushMatrix();if(type==2)g.pose().rotateAbout(-.15f,x,y);
        g.blit(markers,x-32,y-32,x+32,y+32,type/3f,(type+1)/3f,0f,1f);
        g.pose().popMatrix();
    }
    private Identifier artwork(TravelJournal.KnownStamp stamp,boolean load) {
        if(stamp.asset()==null && stamp.item()==null)return null;
        String key=(stamp.id().equals("expert")?"gold/":"wood/")+(stamp.asset()!=null?"asset/"+stamp.asset():"item/"+stamp.item());
        if(art.containsKey(key))return art.get(key);
        if(!load || previewBudget==0 || pending.size()>=2 || pending.contains(key)||failed.contains(key))return null;
        previewBudget--;pending.add(key);int generation=previewGeneration;
        CompletableFuture<java.awt.image.BufferedImage> future;
        try {future=stamp.asset()!=null?CompletableFuture.completedFuture(session.store().image(stamp.asset())):StampArtwork.capture(stamp.item());}
        catch(Exception e){future=CompletableFuture.failedFuture(e);}
        future=future.thenApply(image->dev.postmark.render.StampToolPainter.paint(new StampDefinition("guide/"+stamp.id(),stamp.id(),"preview",false),image));
        future.whenComplete((image,failure)->Minecraft.getInstance().execute(()->{
            if(generation!=previewGeneration)return;pending.remove(key);if(!live || Minecraft.getInstance().screen!=this)return;
            if(failure!=null){failed.add(key);Postmark.LOGGER.debug("Guide stamp preview unavailable: {}",key,failure);return;}
            if(art.size()>=256){var oldest=art.entrySet().iterator();var entry=oldest.next();minecraft.getTextureManager().release(entry.getValue());oldest.remove();}
            art.put(key,registerTexture("stamp",image));
        }));
        return art.get(key);
    }
    private void stampPicture(GuiGraphicsExtractor g,TravelJournal.KnownStamp stamp,int cx,int cy,int size) {
        var texture=artwork(stamp,detail!=null||view.zoom()>=.55||stamp==stamp(hovering,"visitor")||stamp==stamp(hovering,"expert"));float scale=size/64f;
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
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float t) {if(minecraft.level==null)extractPanorama(g,t);extractBlurredBackground(g);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float t) {
        previewBudget=2;layout();long time=now();double dt=(time-lastFrame)/1000.0;lastFrame=time;view.step(dt);
        Hit hover=dragging||detail!=null?null:hit(mx,my);hovering=hover==null?null:hover.venue;
        float amount=(float)(1-Math.exp(-Math.clamp(dt,0,.1)/.075));
        for(var tile:tiles()){UUID id=tile.venue.id();float v=enlargement.getOrDefault(id,0f),target=id.equals(hovering)?1:0;enlargement.put(id,v+(target-v)*amount);}
        g.fill(0,0,width,height,0xAA353E34);g.fill(bx-3,by-3,bx+bw+3,by+bh+3,0x403D4336);
        g.enableScissor(bx,by,bx+bw,by+bh);
        g.fill(bx,by,bx+bw,by+bh,PAPER);
        g.pose().pushMatrix();g.pose().translate((float)(bx+view.x()),(float)(by+view.y()));g.pose().scale((float)view.zoom(),(float)view.zoom());
        paper(g);
        for(var tile:paintOrder()) {
            boolean hot=tile.venue.id().equals(hovering);float cx=tile.x+tile.size/2f,cy=tile.y+tile.size/2f,k=(float)hoverScale(tile.venue.id());
            g.pose().pushMatrix();g.pose().translate(cx,cy);g.pose().scale(k,k);g.pose().translate(-cx,-cy);g.pose().rotateAbout(tile.angle,cx,cy);
            scrap(g,tile,hot);
            var state=states.get(tile.venue.id());
            boolean done=state.complete;
            if(done){g.outline(tile.x-3,tile.y-3,tile.size+6,tile.size+6,0xFF356647);g.outline(tile.x-4,tile.y-4,tile.size+8,tile.size+8,0xFF356647);}
            if(state.searched)marker(g,0,tile.x+tile.size-12,tile.y+tile.size-16);
            for(String kind:List.of("visitor","expert")){var s=stamp(tile.venue.id(),kind);if(s!=null)stampPicture(g,s,kind.equals("visitor")?tile.x:tile.x+tile.size,tile.y,24);}
            if(state.inspection)marker(g,1,tile.x+tile.size/2,tile.y-16);
            else if(done)marker(g,2,tile.x+tile.size/2,tile.y-16);
            long extra=state.extra;
            if(extra>0){g.fill(tile.x-8,tile.y+tile.size-10,tile.x+11,tile.y+tile.size+2,0xFFDFC995);g.text(font,Component.literal("+"+extra),tile.x-6,tile.y+tile.size-8,INK,false);}
            g.pose().popMatrix();
        }
        g.pose().popMatrix();g.disableScissor();
        if(venues.isEmpty())g.centeredText(font,Component.literal(GuideBridge.available()?"等待展馆目录…":"当前世界没有 SMU 展馆"),bx+bw/2,by+bh/2,0xFF7E8467);
        chrome(g);
        if(detail!=null)drawDetail(g,mx,my);
        String hint=hint(detail!=null?hit(mx,my):hover);if(!error.isEmpty())hint=error;else if(hint.isBlank())hint=GuideBridge.status();
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
        String note=areaSearched(detail)?(complete(detail)?"本地搜索已集齐 · 周边64格":"周边64格已检索"):complete(detail)?(preset==null?"普通与大师均已收集":"清单内已集齐"):journal.entry(detail).searched()?(needsInspection(detail)?"已检索 · 仍有未知章位":"已检索"):"尚未检索";
        if(preset!=null)note+=" · 玩家清单 "+StampCatalog.bundled().date();
        g.text(font,Component.literal(font.plainSubstrByWidth(note,w-28)),x+14,y+46,0xFF7A8164,false);
        detailPage=Math.min(detailPage,Math.max(0,(known.size()-1)/detailCapacity()));
        for(int i=0;i<detailCapacity() && detailPage*detailCapacity()+i<known.size();i++) {
            var s=known.get(detailPage*detailCapacity()+i);int cx=x+20+(w-40)*(i%4)/4+(w-40)/8,cy=y+84+(i/4)*52;
            stampPicture(g,s,cx,cy,24);String label=s.id().equals("visitor")?"普通":s.id().equals("expert")?"大师":s.id();
            g.centeredText(font,Component.literal(font.plainSubstrByWidth(label,(w-40)/4-4)),cx,cy+20,s.owned()?INK:0xFF9F967A);
        }
        if(known.isEmpty())g.centeredText(font,Component.literal(areaSearched(detail)?"周边未发现印章":journal.entry(detail).searched()?"尚未发现印章":"等待探索"),x+w/2,y+80,0xFF9B967B);
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
            if(needsInspection(t.venue.id()) && Math.abs(tx-(t.x+t.size/2.0))<=12 && Math.abs(ty-(t.y-16))<=15)return new Hit("inspection",t.venue.id(),null);
            if(!needsInspection(t.venue.id()) && complete(t.venue.id()) && Math.abs(tx-(t.x+t.size/2.0))<=14 && Math.abs(ty-(t.y-16))<=15)return new Hit("complete",t.venue.id(),null);
            if(states.get(t.venue.id()).searched && Math.abs(tx-(t.x+t.size-12))<=22 && Math.abs(ty-(t.y+t.size-16))<=22)return new Hit("footprint",t.venue.id(),null);
            for(String kind:List.of("visitor","expert"))if(stamp(t.venue.id(),kind)!=null && Math.abs(tx-(kind.equals("visitor")?t.x:t.x+t.size))<=17&&ty>=t.y-25&&ty<=t.y+17)return new Hit("stamp",t.venue.id(),kind);
            boolean extra=states.get(t.venue.id()).extra>0;
            if(extra&&tx>=t.x-9&&tx<=t.x+13&&ty>=t.y+t.size-11&&ty<=t.y+t.size+3)return new Hit("details",t.venue.id(),null);
            // The visible paper border belongs to the enlarged tile too, not just its inset image.
            if(tx>=t.x-10&&tx<=t.x+t.size+8&&ty>=t.y+t.size+2&&ty<=t.y+t.size+25)return new Hit("details",t.venue.id(),null);
            if(tx>=t.x-10&&tx<=t.x+t.size+8&&ty>=t.y-10&&ty<t.y+t.size+2)return new Hit("travel",t.venue.id(),null);
        }
        return null;
    }
    private String hint(Hit hit) {
        if(hit==null)return detail==null?"拖动纸面 · 滚轮缩放":"";
        var venue=venues.stream().filter(v->v.id().equals(hit.venue)).findFirst().orElse(null);
        return switch(hit.action){
            case "travel" -> venue!=null?(venue.canTeleport()?"前往 "+venue.name():"这个展馆尚未设置传送点"):"";
            case "details" -> "查看展馆介绍";
            case "inspection" -> journal.entry(hit.venue).searched()?"已检索，仍有章位未确认 · 点击查看":"章位尚未检索 · 点击查看";
            case "complete" -> "已知章已集齐 · 点击查看";
            case "footprint" -> "猫猫踩过啦 · 已检索，点击查看";
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
            case "details","inspection","footprint","complete" -> {detail=target.venue;detailPage=0;}
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
    @Override public void removed() {live=false;if(cameraReady&&!venues.isEmpty())try{session.saveGuideView(view.bookmark());}catch(Exception e){Postmark.LOGGER.warn("Cannot save travel guide view",e);}previewGeneration++;if(markers!=null){minecraft.getTextureManager().release(markers);markers=null;}if(patterns!=null){minecraft.getTextureManager().release(patterns);patterns=null;}icons.clear();for(var id:art.values())minecraft.getTextureManager().release(id);art.clear();pending.clear();}
    @Override public boolean isPauseScreen() {return false;}
}
