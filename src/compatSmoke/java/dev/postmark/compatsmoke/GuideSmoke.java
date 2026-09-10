package dev.postmark.compatsmoke;

import dev.postmark.Postmark;
import dev.postmark.client.*;
import dev.postmark.compat.GuideBridge;
import dev.postmark.model.*;
import dev.postmark.storage.TravelJournalStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.teacon.exhibition_portal.ExhibitionPortal;
import org.teacon.exhibition_portal.client.interaction.StampingCounterBlockEntity;
import org.teacon.exhibition_portal.components.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real SMU packets and block entities in a fresh, explicitly synthetic integration world. */
@EventBusSubscriber(modid="postmark",value=Dist.CLIENT)
public final class GuideSmoke {
    private static final UUID PRESET=UUID.fromString("bf52f38c-3531-54fa-a83c-97eeafd51b54");
    private static final UUID TARGET=new UUID(0,1),EMPTY=new UUID(0,7);
    private static final String[] NAMES={"苔原花园","星轨天文馆","齿轮工坊","远山驿站","潮汐研究所","白桦书屋","云端机场","红石剧场","玻璃温室","夜航码头","余烬锻造坊","风车牧场","极光观测站"};
    private static int ticks,stage,at,y,targetX;
    private static CompletableFuture<?> work;
    private static TravelGuideScreen guide;
    private static String shot;
    private static BlockPos visitor,expert,extra,remote,unloaded;
    private static final UUID REMOTE=new UUID(0,13);
    private static boolean searchedAtArrival;
    private static double zoomBefore,anchorX,anchorY;
    private static void next(){stage++;at=ticks;}
    private static MouseButtonEvent mouse(double x,double y){return new MouseButtonEvent(x,y,new MouseButtonInfo(0,0));}
    private static void click(double x,double y){var mc=Minecraft.getInstance();var screen=mc.screen;if(!net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(screen,mouse(x,y),false))screen.mouseClicked(mouse(x,y),false);if(mc.screen==screen&&!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(screen,mouse(x,y)))screen.mouseReleased(mouse(x,y));}
    private static GuideEntry inventoryEntry() {return (GuideEntry)Minecraft.getInstance().screen.children().stream().filter(GuideEntry.class::isInstance).findFirst().orElseThrow();}
    private static void dragEntry(int x,int y) {
        var mc=Minecraft.getInstance();var screen=mc.screen;var entry=inventoryEntry();double px=entry.getX()+27,py=entry.getY()+20;
        var hooks=new net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre(screen,mouse(px,py),false);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(hooks);if(!hooks.isCanceled())throw new AssertionError("Entry press leaked to container");
        if(!net.neoforged.neoforge.client.ClientHooks.onScreenMouseDragPre(screen,mouse(x+27,y+20),x+27-px,y+20-py))throw new AssertionError("Entry drag leaked to container");
        if(!net.neoforged.neoforge.client.ClientHooks.onScreenMouseReleasedPre(screen,mouse(x+27,y+20)))throw new AssertionError("Entry release leaked to container");
        if(mc.screen!=screen)throw new AssertionError("Dragging entry opened guide");
    }
    private static void verifyEntryDrag() throws Exception {
        var mc=Minecraft.getInstance();var previous=mc.gameMode.getPlayerMode();
        for(var mode:List.of(GameType.SURVIVAL,GameType.CREATIVE)) {
            mc.gameMode.setLocalMode(mode);mc.setScreen(new InventoryScreen(mc.player));
            var menu=((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.screen).getMenu();
            var before=menu.getCarried();var held=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE,7);menu.setCarried(held);
            dragEntry(-200,-200);if(inventoryEntry().getX()!=6||inventoryEntry().getY()!=6)throw new AssertionError("Entry escaped screen bounds");
            dragEntry(24,65);if(!net.minecraft.world.item.ItemStack.matches(held,menu.getCarried()))throw new AssertionError("Entry drag changed carried item");menu.setCarried(before);
            var saved=new dev.postmark.storage.GuideEntryPositionStore(mc.gameDirectory.toPath()).load();
            if(saved==null)throw new AssertionError("Entry position not saved");
            mc.setScreen(new InventoryScreen(mc.player));var entry=inventoryEntry();
            if(entry.getX()!=24||entry.getY()!=65)throw new AssertionError("Reopened inventory lost entry position");
        }
        mc.gameMode.setLocalMode(previous);
    }
    private static void verifyReopenCamera() throws Exception {
        var mc=Minecraft.getInstance();var before=guide;
        var point=guide.iconCenter(TARGET);double zoom=guide.zoomLevel();
        // Closing persists the view; a new screen must restore it without another entrance zoom.
        guide.onClose();TravelGuideScreen.show(mc.screen);guide=(TravelGuideScreen)mc.screen;
        var after=guide.iconCenter(TARGET);
        if(Math.abs(zoom-guide.zoomLevel())>.001 || Math.hypot(point[0]-after[0],point[1]-after[1])>1)throw new AssertionError("Reopened guide lost camera");
        if(new dev.postmark.storage.GuideViewStore(ClientSession.get().store().directory()).load()==null)throw new AssertionError("Guide camera not persisted");
    }
    private static GuideVenue venue(UUID id) {return GuideBridge.venues().stream().filter(v->v.id().equals(id)).findFirst().orElseThrow();}
    private static void open() {TravelGuideScreen.show(null);guide=(TravelGuideScreen)Minecraft.getInstance().screen;}
    private static void counter(net.minecraft.server.level.ServerLevel level,BlockPos pos,String id,Identifier item) {
        counter(level,pos,id,item,TARGET);
    }
    private static void counter(net.minecraft.server.level.ServerLevel level,BlockPos pos,String id,Identifier item,UUID venue) {
        var state=ExhibitionPortal.STAMPING_COUNTER.get().defaultBlockState();level.setBlock(pos,state,3);
        var entity=(StampingCounterBlockEntity)level.getBlockEntity(pos);
        try{for(var e:Map.of("exhibition",venue,"stampID",id,"item",item).entrySet()){var field=StampingCounterBlockEntity.class.getDeclaredField(e.getKey());field.setAccessible(true);field.set(entity,e.getValue());}}
        catch(Exception e){throw new RuntimeException(e);}
        entity.setChanged();level.sendBlockUpdated(pos,state,state,3);
    }
    private static ExhibitionStamp granted(String id){return new ExhibitionStamp(id,Identifier.parse("minecraft:grass_block"),new org.teacon.exhibition_portal.client.framework.components.Rectangle(.4f,.4f,.05f,.05f),0);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("postmark.guideSmoke"))return;
        var mc=Minecraft.getInstance();ticks++;
        try{
            if(stage==99)return;if(ticks>4400)throw new AssertionError("Guide smoke timeout, stage "+stage);
            if(ticks%100==0)Postmark.LOGGER.info("Guide smoke stage {} at tick {}, screen {}",stage,ticks,mc.screen==null?"world":mc.screen.getClass().getSimpleName());
            if(stage==0 && mc.getOverlay()==null && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
                mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());
            }
            if(stage==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null && ticks>40){
                mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);next();
                mc.createWorldOpenFlows().createFreshLevel("Postmark-guide-"+System.currentTimeMillis(),new LevelSettings("Postmark synthetic guide fixture",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),new WorldOptions(1879L,false,false),WorldPresets::createFlatWorldDimensions,new TitleScreen());
            }else if(stage==1 && mc.player!=null && mc.level!=null && mc.screen==null && GuideBridge.venues().size()==15){
                y=mc.player.blockPosition().getY();targetX=mc.player.blockPosition().getX()+512;
                visitor=new BlockPos(targetX+2,y,32);expert=new BlockPos(targetX-2,y,32);extra=new BlockPos(targetX,y,34);
                work=mc.getSingleplayerServer().submit(()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    for(int i=0;i<13;i++){final int n=i;UUID id=new UUID(0,i+1);EPServer.updateMetadata(id,m->m.withName(NAMES[n]).withDescription("导览界面测试展馆 · 示例数据").withWaypoint(new ExhibitionWaypoint(targetX+n*160,y,32,0,0)));}
                    EPServer.updateMetadata(PRESET,m->m.withName("玩家清单测试馆").withDescription("预设章位演示 · 示例数据").withWaypoint(new ExhibitionWaypoint(targetX+2200,y,32,0,0)));
                    EPServer.updateFootprint(player,TARGET,f->f.withMark(ExhibitionFootprint.MARK_VISITED));
                    for(int i:new int[]{2,3,5,8,10}){UUID id=new UUID(0,i+1);EPServer.updateFootprint(player,id,f->f.withStamp(granted("visitor")));if(i==3||i==8)EPServer.updateFootprint(player,id,f->f.withStamp(granted("expert")));}
                    var level=player.level();counter(level,visitor,"visitor",Identifier.parse("minecraft:grass_block"));counter(level,expert,"expert",Identifier.parse("minecraft:paper"));counter(level,visitor.offset(2,0,0),"visitor",Identifier.parse("minecraft:grass_block"));
                });next();
            }else if(stage==2 && work.isDone() && ticks-at>45){
                work.join();var session=ClientSession.get();
                if(session.journal().entry(TARGET).searched())throw new AssertionError("SMU #visited must not create a survey footprint");
                var found=new ArrayList<TravelJournal.Discovery>();var searched=new HashSet<UUID>();
                for(int i:new int[]{1,2,3,4,5,7,8,10,11}){UUID id=new UUID(0,i+1);searched.add(id);if(i!=1)found.add(new TravelJournal.Discovery(id,"visitor","minecraft:grass_block"));if(i==2||i==3||i==7||i==8||i==11)found.add(new TravelJournal.Discovery(id,"expert","minecraft:paper"));}
                session.updateJournal(session.journal().surveyed(searched,found));
                PostcardScreen.show(null,null);next();
            }else if(stage==3 && ticks-at==8){
                shot="01-guide-desk-entry.png";
            }else if(stage==3 && ticks-at>12){
                click(GuideEntry.deskX(mc.screen.width),GuideEntry.deskY(mc.screen.height));if(!(mc.screen instanceof TravelGuideScreen))throw new AssertionError("Mouse desk entry failed");guide=(TravelGuideScreen)mc.screen;next();
            }else if(stage==4 && ticks-at==8){var p=guide.fitCenter();click(p[0],p[1]);
            }else if(stage==4 && ticks-at==18){shot="02-magazine-overview.png";
                if(!guide.needsInspection(TARGET) || !guide.needsInspection(PRESET))throw new AssertionError("Unsearched galleries need a question mark");
            }else if(stage==4 && ticks-at==20){
                var p=guide.inspectionCenter(PRESET);click(p[0],p[1]);shot="14-preset-detail.png";
            }else if(stage==4 && ticks-at>22){
                if(!PRESET.equals(guide.detailVenue()))throw new AssertionError("Preset detail did not open");
                var session=ClientSession.get();var raw=session.journal().stamps(PRESET,session.album().stamps());
                if(!raw.isEmpty() || session.journal().entry(PRESET).searched())throw new AssertionError("Preset changed observation or ownership");
                if(StampCatalog.bundled().merge(PRESET,raw).size()!=2)throw new AssertionError("Preset ghosts missing");
                guide.onClose();
                if(guide.canvasVenueCount()!=15)throw new AssertionError("All fifteen venues must share one canvas");
                for(var venue:GuideBridge.venues()) {
                    var caption=guide.captionCenter(venue.id());click(caption[0],caption[1]);
                    if(!venue.id().equals(guide.detailVenue()))throw new AssertionError("Canvas caption obscured: "+venue.id());
                    guide.onClose();
                }
                var p=guide.iconCenter(TARGET);anchorX=p[0];anchorY=p[1];zoomBefore=guide.zoomLevel();

                guide.mouseScrolled(p[0],p[1],0,3);next();
            }else if(stage==5 && ticks-at>14){
                if(guide.zoomLevel()<zoomBefore*1.3)throw new AssertionError("Wheel zoom did not animate");
                var anchor=guide.iconCenter(TARGET);
                if(Math.hypot(anchor[0]-anchorX,anchor[1]-anchorY)>1)throw new AssertionError("Cursor zoom drifted");
                if(guide.hoverScale(TARGET)<1.10)throw new AssertionError("Native mouse hover did not enlarge paper: "+guide.hoverScale(TARGET)+" mouse="+mc.mouseHandler.getScaledXPos(mc.getWindow())+","+mc.mouseHandler.getScaledYPos(mc.getWindow())+" target="+anchorX+","+anchorY);
                var edge=guide.captionEdge(TARGET);click(edge[0],edge[1]);
                if(!TARGET.equals(guide.detailVenue()))throw new AssertionError("Enlarged caption border did not follow hover and wheel zoom");
                guide.onClose();
                shot="03-canvas-hover-zoom.png";
                stage=50;at=ticks;
            }else if(stage==50 && ticks-at==6){var p=guide.fitCenter();click(p[0],p[1]);
            }else if(stage==50 && ticks-at>18){
                var p=guide.cornerCenter(new UUID(0,3),true);double px=mc.player.getX();click(p[0],p[1]);
                if(guide.detailVenue()==null || mc.player.getX()!=px || mc.screen!=guide)throw new AssertionError("Unowned corner must open details, not teleport");shot="04-stamp-detail.png";stage=6;at=ticks;
            }else if(stage==6 && ticks-at>10){
                guide.onClose();var p=guide.iconCenter(TARGET);
                // A press dragged onto another tile must never send a teleport.
                guide.mouseClicked(mouse(p[0],p[1]),false);guide.mouseDragged(mouse(p[0]+40,p[1]+20),40,20);guide.mouseReleased(mouse(p[0]+40,p[1]+20));if(mc.screen!=guide)throw new AssertionError("Dragged click teleported");
                var moved=guide.iconCenter(TARGET);if(Math.abs(moved[0]-p[0]-40)>1 || Math.abs(moved[1]-p[1]-20)>1)throw new AssertionError("Canvas did not pan with drag");
                click(moved[0],moved[1]);if(mc.screen!=null)throw new AssertionError("Icon must send SMU teleport and close");
                if(ClientSession.get().journal().entry(TARGET).searched())throw new AssertionError("Sending a teleport must not mark searched");next();
            }else if(stage==7 && Math.abs(mc.player.getX()-(targetX+.5))<1 && Math.abs(mc.player.getZ()-32.5)<1){
                searchedAtArrival=ClientSession.get().journal().entry(TARGET).searched();if(searchedAtArrival)throw new AssertionError("Arrival alone granted footprint");next();
            }else if(stage==8 && ClientSession.get().journal().entry(TARGET).searched() && ClientSession.get().journal().entry(TARGET).stamps().size()==2){
                var session=ClientSession.get();var record=session.journal().entry(TARGET);
                if(record.stamps().size()!=2)throw new AssertionError("Nearby visitor/expert discovery or duplicate counter de-duplication failed: "+record);
                if(!session.journal().stamps(TARGET,session.album().stamps()).stream().noneMatch(TravelJournal.KnownStamp::owned))throw new AssertionError("Discovery granted a stamp");
                if(!new TravelJournalStore(session.store().directory()).load().equals(session.journal()))throw new AssertionError("Survey not persisted");
                open();next();
            }else if(stage==9 && ticks-at==10){shot="05-surveyed-uncollected.png";
                if(guide.needsInspection(TARGET))throw new AssertionError("Observed visitor and expert should resolve question");
                var p=guide.footprintCenter(TARGET);click(p[0],p[1]);
                if(!TARGET.equals(guide.detailVenue()))throw new AssertionError("Footprint must open details without teleport");
                guide.onClose();
            }else if(stage==9 && ticks-at>15){
                mc.setScreen(null);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(visitor),Direction.UP,visitor,false));next();
            }else if(stage==10 && ClientSession.get().album().stamps().stream().anyMatch(s->s.key().equals(TARGET+"/visitor"))){
                mc.setScreen(null);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(expert),Direction.UP,expert,false));next();
            }else if(stage==11 && ClientSession.get().album().stamps().stream().anyMatch(s->s.key().equals(TARGET+"/expert"))){
                if(!TravelJournal.regularComplete(ClientSession.get().journal().stamps(TARGET,ClientSession.get().album().stamps())))throw new AssertionError("Regular completion missing");open();next();
            }else if(stage==12 && ticks-at==1){var p=guide.iconCenter(TARGET);guide.mouseScrolled(p[0],p[1],0,8);
            }else if(stage==12 && ticks-at==10){shot="06-regular-complete.png";
                var p=guide.inspectionCenter(TARGET);click(p[0],p[1]);
                if(!TARGET.equals(guide.detailVenue()))throw new AssertionError("Completion tag must open venue details");
                guide.onClose();
            }else if(stage==12 && ticks-at>15){
                work=mc.getSingleplayerServer().submit(()->counter(mc.getSingleplayerServer().overworld(),extra,"special",Identifier.parse("minecraft:diamond")));next();
            }else if(stage==13 && work.isDone() && ClientSession.get().journal().entry(TARGET).stamps().size()==3){
                work.join();if(TravelJournal.regularComplete(ClientSession.get().journal().stamps(TARGET,ClientSession.get().album().stamps())))throw new AssertionError("Extra unowned stamp did not revoke completion");shot="07-extra-stamp.png";next();
            }else if(stage==14 && ticks-at>15){
                var p=guide.cornerCenter(TARGET,false);click(p[0],p[1]);if(!(mc.screen instanceof PostcardScreen))throw new AssertionError("Owned stamp did not open postcard");shot="08-owned-stamp-pickup.png";next();
            }else if(stage==15 && ticks-at>12){
                var desk=(PostcardScreen)mc.screen;int before=ClientSession.get().album().selected().imprints().size();
                click(desk.width/2.0,desk.height/2.0);next();
            }else if(stage==16 && ticks-at>12){
                if(ClientSession.get().album().selected().imprints().isEmpty())throw new AssertionError("Picked-up guide stamp did not stamp");
                mc.setScreen(new InventoryScreen(mc.player));next();
            }else if(stage==17 && ticks-at==8){
                if(mc.screen.children().stream().noneMatch(GuideEntry.class::isInstance))throw new AssertionError("Inventory guide entry missing");verifyEntryDrag();shot="09-inventory-entry.png";
            }else if(stage==17 && ticks-at>12){
                var entry=(GuideEntry)mc.screen.children().stream().filter(GuideEntry.class::isInstance).findFirst().orElseThrow();
                click(entry.getX()+27,entry.getY()+20);if(!(mc.screen instanceof TravelGuideScreen))throw new AssertionError("Inventory mouse entry failed");guide=(TravelGuideScreen)mc.screen;verifyReopenCamera();next();
            }else if(stage==18 && ticks-at==6){shot="13-inventory-canvas-readable.png";
            }else if(stage==18 && ticks-at==7){var p=guide.fitCenter();click(p[0],p[1]);
            }else if(stage==18 && ticks-at==18){shot="12-inventory-canvas-entry.png";
            }else if(stage==18 && ticks-at>22){
                var p=guide.stampDeskCenter();click(p[0],p[1]);if(!(mc.screen instanceof PostcardScreen))throw new AssertionError("Inventory canvas stamp entry failed");stage=51;at=ticks;
            }else if(stage==51 && ticks-at>12){
                mc.screen.onClose();if(mc.screen!=guide)throw new AssertionError("Stamp desk must return to the same canvas");stage=52;at=ticks;
            }else if(stage==52 && ticks-at>12){
                var p=guide.iconCenter(EMPTY);click(p[0],p[1]);stage=19;at=ticks;
            }else if(stage==19 && Math.abs(mc.player.getX()-(targetX+6*160+.5))<1 && ClientSession.get().journal().areaSearched(venue(EMPTY))){
                // A real completed empty survey must not invent either regular stamp slot.
                var entry=ClientSession.get().journal().entry(EMPTY);if(!entry.searched()||!entry.stamps().isEmpty())throw new AssertionError("Empty survey semantics");
                open();next();
            }else if(stage==20 && ticks-at==10){shot="10-final-magazine.png";
                if(guide.needsInspection(EMPTY))throw new AssertionError("Completed empty area should resolve local question");
            }else if(stage==20 && ticks-at>15){
                mc.options.guiScale().set(3);next();
            }else if(stage==21 && ticks-at==10){shot="11-collage-gui3.png";
            }else if(stage==21 && ticks-at>15){
                var p=guide.captionCenter(TARGET);click(p[0],p[1]);
                if(!TARGET.equals(guide.detailVenue()))throw new AssertionError("Rotated caption must open its own venue at GUI 3");
                guide.onClose();if(guide.canvasVenueCount()!=15)throw new AssertionError("Resizing dropped venues from the canvas");
                mc.options.guiScale().set(2);next();
            }else if(stage==22 && ticks-at>10){
                mc.setScreen(null);remote=mc.player.blockPosition().offset(96,0,0);unloaded=remote.offset(4096,0,0);
                if(mc.level.getChunkSource().getChunk(remote.getX()>>4,remote.getZ()>>4,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)==null)throw new AssertionError("Remote test chunk must already be loaded");
                work=mc.getSingleplayerServer().submit(()->{
                    var level=mc.getSingleplayerServer().overworld();
                    EPServer.updateMetadata(REMOTE,m->m.withWaypoint(new ExhibitionWaypoint(remote.getX(),remote.getY(),remote.getZ(),0,0)));
                    counter(level,remote,"visitor",Identifier.parse("minecraft:paper"),REMOTE);
                    counter(level,unloaded,"expert",Identifier.parse("minecraft:emerald"),REMOTE);
                });next();
            }else if(stage==23 && work.isDone() && ClientSession.get().journal().entry(REMOTE).stamps().stream().anyMatch(s->s.id().equals("visitor"))){
                work.join();var entry=ClientSession.get().journal().entry(REMOTE);
                if(Math.abs(mc.player.getX()-remote.getX())<64 || !entry.searched() || entry.stamps().size()!=1)throw new AssertionError("Loaded-chunk survey range or footprint failed: "+entry);
                if(mc.level.getChunkSource().getChunk(unloaded.getX()>>4,unloaded.getZ()>>4,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)!=null)throw new AssertionError("Unloaded test chunk was requested");
                if(!ClientSession.get().journal().stamps(REMOTE,ClientSession.get().album().stamps()).stream().noneMatch(TravelJournal.KnownStamp::owned))throw new AssertionError("Remote discovery granted ownership");
                work=mc.getSingleplayerServer().submit(()->counter(mc.getSingleplayerServer().overworld(),remote,"visitor",Identifier.parse("minecraft:diamond"),REMOTE));
                // Moving more than the old eight-block limit must not discard observations.
                mc.player.setPos(mc.player.getX()+12,mc.player.getY(),mc.player.getZ());next();
            }else if(stage==24 && work.isDone() && ClientSession.get().journal().entry(REMOTE).stamps().stream().anyMatch(s->s.item().equals("minecraft:diamond"))){
                work.join();if(ClientSession.get().journal().entry(REMOTE).stamps().size()!=1)throw new AssertionError("Repeat scan duplicated a stamp or inspected an unloaded chunk");
                next();
            }else if(stage==25 && ClientSession.get().journal().areaSearched(venue(REMOTE))){
                var session=ClientSession.get();var observed=session.journal().stamps(REMOTE,session.album().stamps());
                if(StampCatalog.bundled().needsInspection(REMOTE,true,observed,true) || StampCatalog.bundled().complete(REMOTE,observed,true))throw new AssertionError("Single unowned area result");
                if(!new TravelJournalStore(session.store().directory()).load().areaSearched(venue(REMOTE)))throw new AssertionError("Area result not persisted");
                work=mc.getSingleplayerServer().submit(()->EPServer.updateFootprint(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst(),REMOTE,f->f.withStamp(granted("visitor"))));next();
            }else if(stage==26 && ClientSession.get().album().stamps().stream().anyMatch(s->s.key().equals(REMOTE+"/visitor"))){
                work.join();var session=ClientSession.get();var observed=session.journal().stamps(REMOTE,session.album().stamps());
                if(!StampCatalog.bundled().complete(REMOTE,observed,session.journal().areaSearched(venue(REMOTE))))throw new AssertionError("Collected single-stamp venue should complete");
                open();var p=guide.captionCenter(REMOTE);click(p[0],p[1]);shot="15-single-area-result.png";
                work=mc.getSingleplayerServer().submit(()->counter(mc.getSingleplayerServer().overworld(),remote.offset(2,0,0),"expert",Identifier.parse("minecraft:emerald"),REMOTE));next();
            }else if(stage==27 && ClientSession.get().journal().entry(REMOTE).stamps().size()==2){
                work.join();var session=ClientSession.get();
                if(StampCatalog.bundled().complete(REMOTE,session.journal().stamps(REMOTE,session.album().stamps()),session.journal().areaSearched(venue(REMOTE))))throw new AssertionError("New unowned stamp must revoke single completion");
                next();
            }else if(stage==28){
                mc.setScreen(null);
                work=mc.getSingleplayerServer().submit(()->{
                    var pos=remote.offset(4,0,0);
                    EPServer.updateMetadata(PRESET,m->m.withWaypoint(new ExhibitionWaypoint(pos.getX(),pos.getY(),pos.getZ(),0,0)));
                    counter(mc.getSingleplayerServer().overworld(),pos,"expert",Identifier.parse("minecraft:emerald"),PRESET);
                });next();
            }else if(stage==29 && work.isDone() && ClientSession.get().journal().areaSearched(venue(PRESET))
                    && ClientSession.get().journal().entry(PRESET).stamps().stream().anyMatch(s->s.id().equals("expert"))){
                work.join();var session=ClientSession.get();var observed=session.journal().stamps(PRESET,session.album().stamps());
                if(observed.size()!=1 || !observed.getFirst().id().equals("expert"))throw new AssertionError("Master-only discovery fixture");
                if(StampCatalog.bundled().merge(PRESET,observed,true).size()!=1 || StampCatalog.bundled().needsInspection(PRESET,true,observed,true) || StampCatalog.bundled().complete(PRESET,observed,true))throw new AssertionError("Preset ordinary ghost must clear after full master-only survey without granting ownership");
                work=mc.getSingleplayerServer().submit(()->EPServer.updateFootprint(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst(),PRESET,f->f.withStamp(granted("expert"))));next();
            }else if(stage==30 && ClientSession.get().album().stamps().stream().anyMatch(s->s.key().equals(PRESET+"/expert"))){
                work.join();var session=ClientSession.get();var observed=session.journal().stamps(PRESET,session.album().stamps());
                if(!StampCatalog.bundled().complete(PRESET,observed,true))throw new AssertionError("Owned master-only venue should complete despite paired preset");
                open();var p=guide.captionCenter(PRESET);click(p[0],p[1]);next();
            }else if(stage==31 && ticks-at==10){shot="16-master-only-area-result.png";
                if(guide.needsInspection(PRESET))throw new AssertionError("Guide still shows inspection question for master-only venue");
            }else if(stage==31 && ticks-at>15){
                mc.setScreen(null);work=mc.getSingleplayerServer().submit(()->counter(mc.getSingleplayerServer().overworld(),remote.offset(6,0,0),"visitor",Identifier.parse("minecraft:paper"),PRESET));next();
            }else if(stage==32 && ClientSession.get().journal().entry(PRESET).stamps().size()==2){
                work.join();var session=ClientSession.get();var observed=session.journal().stamps(PRESET,session.album().stamps());
                if(StampCatalog.bundled().merge(PRESET,observed,true).size()!=2 || StampCatalog.bundled().complete(PRESET,observed,true))throw new AssertionError("Later ordinary discovery must restore slot and revoke master-only completion");
                next();
            }else if(stage==33){
                Files.writeString(mc.gameDirectory.toPath().resolve("guide-result.txt"),"PASS: master-only venue overrides paired preset after full area scan, unowned master does not complete, owned master completes, later ordinary discovery restores slot and revokes completion; survival and creative inventory entry drag captures press/drag/release without moving carried items; screen bounds and persisted position on reopen; guide camera restored from disk; unsearched preset and ordinary venues show question; observed slots remove question; completed waypoint area resolves single or empty venue questions; single collection completes and new expert revokes completion; area evidence persists; question, completion tag and cat footprint open details; loaded client chunk discovery 96 blocks away; unloaded server counter ignored; repeat scan updates artwork after movement; real SMU 1.1.12 gallery icons and metadata; bundled preset ghosts without discovery or ownership; all 15 venues on one canvas and every caption; cursor-anchored smooth zoom; native mouse event hover enlargement; canvas drag without teleport; inventory stamp desk and return; canvas GUI 2 and 3; rotated caption and corner targets; desk and inventory mouse entries; isolated stamp hit targets; drag cancellation; real UUID teleport; SMU #visited and arrival alone never mark searched; completed loaded-chunk survey records two unowned stamps and de-duplicates counters; separate journal persists; actual counter interactions collect visitor/expert; regular completion; newly discovered third stamp revokes completion; owned stamp pickup and postcard imprint; empty survey has no invented slots. Synthetic exhibition fixtures, not a production server.");
                mc.stop();stage=99;
            }
        }catch(Throwable e){Postmark.LOGGER.error("GUIDE SMOKE FAILED stage="+stage,e);try{Files.writeString(mc.gameDirectory.toPath().resolve("guide-result.txt"),"FAIL stage="+stage+": "+e);}catch(Exception ignored){}mc.stop();stage=99;}
    }
    @SubscribeEvent public static void hoverInput(RenderFrameEvent.Pre event) {
        if(!Boolean.getBoolean("postmark.guideSmoke") || !(stage==5 || stage==50 && ticks-at<6))return;
        // Drive Minecraft's actual mouse event path before rendering without moving the user's desktop cursor.
        var mc=Minecraft.getInstance();
        try {
            var move=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove",long.class,double.class,double.class);move.setAccessible(true);
            move.invoke(mc.mouseHandler,mc.getWindow().handle(),anchorX*mc.getWindow().getScreenWidth()/guide.width,anchorY*mc.getWindow().getScreenHeight()/guide.height);
        }catch(Exception e){throw new RuntimeException(e);}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){if(shot==null)return;String name=shot;shot=null;var mc=Minecraft.getInstance();Screenshot.takeScreenshot(mc.getMainRenderTarget(),image->{try(image){Path folder=mc.gameDirectory.toPath().resolve("guide-shots");Files.createDirectories(folder);image.writeToFile(folder.resolve(name));}catch(Exception e){Postmark.LOGGER.error("Guide screenshot failed",e);}});}
}
