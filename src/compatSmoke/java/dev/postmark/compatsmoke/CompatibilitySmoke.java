package dev.postmark.compatsmoke;

import dev.postmark.Postmark;
import dev.postmark.client.ClientSession;
import dev.postmark.client.PostcardScreen;
import dev.postmark.model.MapPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
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
import org.teacon.exhibition_portal.client.EPClient;
import org.teacon.exhibition_portal.client.framework.binding.RenderAccess;
import org.teacon.exhibition_portal.client.interaction.StampingCounterBlockEntity;
import org.teacon.exhibition_portal.components.EPServer;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid="postmark",value=Dist.CLIENT)
public final class CompatibilitySmoke {
    private static final UUID EXHIBITION=UUID.fromString("5af911b3-ab9b-4fb3-81ab-1bd1f332e619");
    private static int ticks,stage,stageAt;
    private static BlockPos counter,secondCounter;
    private static MapPlacement.Rect expected;
    private static CompletableFuture<?> work;
    private static String shot;
    private static boolean animationSeen;
    private static CompletableFuture<java.awt.image.BufferedImage> paperCapture, pngCapture;
    private static void next() { stage++; stageAt=ticks; }
    private static void prepareCounter(String kind,String item) {
        var mc=Minecraft.getInstance();secondCounter=counter.offset(0,0,1);
        work=mc.getSingleplayerServer().submit(()->{
            var level=mc.getSingleplayerServer().overworld();var state=ExhibitionPortal.STAMPING_COUNTER.get().defaultBlockState();level.setBlock(secondCounter,state,3);
            var entity=(StampingCounterBlockEntity)level.getBlockEntity(secondCounter);
            try {for(var entry:java.util.Map.of("exhibition",EXHIBITION,"stampID",kind,"item",Identifier.parse(item)).entrySet()) {
                var field=StampingCounterBlockEntity.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(entity,entry.getValue());
            }} catch(Exception e){throw new RuntimeException(e);}
            entity.setChanged();level.sendBlockUpdated(secondCounter,state,state,3);
        });
    }
    private static void clickCounter(BlockPos pos) {
        var mc=Minecraft.getInstance();mc.setScreen(null);
        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
    }
    private static void assertVariant(String kind,String item,int count) throws Exception {
        var session=ClientSession.get();String key=dev.postmark.model.StampIdentity.key(EXHIBITION,kind,item);
        if(session.album().stamps().stream().noneMatch(s->s.key().equals(key)))throw new AssertionError("Missing clicked variant "+key+"; "+dev.postmark.compat.SignMeUpBridge.notice());
        if(session.album().stamps().stream().filter(s->s.key().startsWith(EXHIBITION+"/")).count()!=count)throw new AssertionError("Variant overwritten after real counter click");
        if(!session.store().load().stamps().equals(session.album().stamps()))throw new AssertionError("Variant not saved");
        var field=PostcardScreen.class.getDeclaredField("tool");field.setAccessible(true);
        var held=(dev.postmark.model.StampDefinition)field.get(Minecraft.getInstance().screen);
        if(held==null || !held.key().equals(key))throw new AssertionError("Counter selected wrong tool "+held);
        var map=RenderAccess.get(EPClient.GALLERY_LOOKUP).get(EXHIBITION).footprint().stamps().stream().filter(s->s.id().equals(kind)).findFirst().orElseThrow();
        if(!map.item().toString().equals(item))throw new AssertionError("SMU map has wrong artwork after counter click: "+map.item());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("postmark.compatSmoke") || Boolean.getBoolean("postmark.guideSmoke")) return;
        var mc=Minecraft.getInstance(); ticks++;
        try {
            if(mc.screen instanceof PostcardScreen screen && (stage==8 || stage==9 || stage==13)) {
                var field=PostcardScreen.class.getDeclaredField("acquisitionAnimation");field.setAccessible(true);
                var animation=(dev.postmark.client.StampAcquisitionAnimation)field.get(screen);
                if(stage==9 && animation!=null)throw new AssertionError("Owned stamp replayed acquisition animation");
                if(animation!=null && animation.elapsed(System.nanoTime()/1_000_000)>450 && !animationSeen) {
                    animationSeen=true;shot="acquisition-"+stage+".png";
                }
            }
            if(ticks>2400) throw new AssertionError("Compatibility test timed out at stage "+stage);
            if(stage==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null && ticks>40) {
                next();
                mc.createWorldOpenFlows().createFreshLevel("Postmark-compat-"+System.currentTimeMillis(),
                        new LevelSettings("Postmark integration fixture",GameType.CREATIVE,
                                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),
                        new WorldOptions(1879L,false,false),WorldPresets::createFlatWorldDimensions,new TitleScreen());
            } else if(stage==1 && mc.player!=null && mc.level!=null && mc.screen==null && mc.getSingleplayerServer()!=null) {
                counter=mc.player.blockPosition().offset(1,0,0); next();
                var server=mc.getSingleplayerServer();
                work=server.submit(()->{
                    EPServer.updateMetadata(EXHIBITION,m->m.withName("联动花园"));
                    var player=server.getPlayerList().getPlayers().getFirst();
                    var level=player.level();
                    var state=ExhibitionPortal.STAMPING_COUNTER.get().defaultBlockState();
                    level.setBlock(counter,state,3);
                    var entity=(StampingCounterBlockEntity)level.getBlockEntity(counter);
                    try {
                        for(var entry:java.util.Map.of("exhibition",EXHIBITION,"stampID","visitor","item",Identifier.parse("minecraft:grass_block")).entrySet()) {
                            var field=StampingCounterBlockEntity.class.getDeclaredField(entry.getKey()); field.setAccessible(true); field.set(entity,entry.getValue());
                        }
                    } catch(Exception e) { throw new RuntimeException(e); }
                    entity.setChanged(); level.sendBlockUpdated(counter,state,state,3);
                });
            } else if(stage==2 && ticks-stageAt>35 && work.isDone()
                    && mc.level.getBlockEntity(counter) instanceof StampingCounterBlockEntity entity && entity.getStampID()!=null
                    && RenderAccess.get(EPClient.GALLERY_LOOKUP).containsKey(EXHIBITION)) {
                work.join(); next();
                org.teacon.exhibition_portal.client.screens.MapLayouts.MAP_AREA.set(new org.teacon.exhibition_portal.client.framework.components.Rectangle(-1500,-1500,4000,4000));
                expected=MapPlacement.at(mc.player.getX(),mc.player.getZ(),-1000,-1000,2000,2000,1.0,30.0*19/32*1.15/4000);
                mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(counter),Direction.UP,counter,false));
            } else if(stage==3 && ticks-stageAt>50) {
                if(!(mc.screen instanceof PostcardScreen)) throw new AssertionError("Counter did not open Postmark; screen="+mc.screen);
                var exhibit=RenderAccess.get(EPClient.GALLERY_LOOKUP).get(EXHIBITION);
                var stamp=exhibit.footprint().stamps().stream().filter(s->s.id().equals("visitor")).findFirst().orElseThrow();
                var location=stamp.location();
                if(Math.abs(location.x()-expected.x())>.00001 || Math.abs(location.y()-expected.y())>.00001
                        || Math.abs(location.w()-expected.width())>.00001 || Math.abs(location.h()-expected.height())>.00001)
                    throw new AssertionError("Map stamp was not synchronized at the player location: "+location+" expected "+expected);
                if(ClientSession.get().album().stamps().stream().noneMatch(s->s.key().equals(dev.postmark.model.StampIdentity.key(EXHIBITION,"visitor","minecraft:grass_block")) && !s.practice()))
                    throw new AssertionError("Confirmed stamp was not added to the postcard box");
                var server=mc.getSingleplayerServer();
                work=server.submit(()->{
                    var player=server.getPlayerList().getPlayers().getFirst();
                    var saved=EPServer.getFootprint(player,EXHIBITION).stamps().getFirst().location();
                    if(Math.abs(saved.x()-expected.x())>.00001) throw new AssertionError("Server did not persist map placement");
                });
                shot="04-signmeup-counter.png"; next();
            } else if(stage==4 && ticks-stageAt>20 && work.isDone()) {
                work.join();
                work=mc.getSingleplayerServer().submit(()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    var expert=new org.teacon.exhibition_portal.components.ExhibitionStamp("expert",Identifier.parse("minecraft:grass_block"),
                            new org.teacon.exhibition_portal.client.framework.components.Rectangle(.49f,.49f,.005f,.005f),0);
                    EPServer.updateFootprint(player,EXHIBITION,footprint->footprint.withStamp(expert));
                });
                paperCapture=dev.postmark.render.StampArtwork.capture("minecraft:paper");
                pngCapture=dev.postmark.render.StampArtwork.capture("exhibition_portal:textures/test_stamp.png");
                next();
            } else if(stage==5 && paperCapture.isDone() && pngCapture.isDone() && work.isDone()
                    && ClientSession.get().album().stamps().stream().anyMatch(s->s.key().equals(dev.postmark.model.StampIdentity.key(EXHIBITION,"expert","minecraft:grass_block")))) {
                var paper=paperCapture.join(); var png=pngCapture.join();
                if(paper.getWidth()!=16*(int)mc.getWindow().getGuiScale() || (paper.getRGB(0,0)>>>24)!=0) throw new AssertionError("Item snapshot must retain transparent background");
                if(png.getWidth()<16) throw new AssertionError("Raw texture stamp failed");
                var session=ClientSession.get();
                var definition=session.album().stamps().stream().filter(s->s.key().equals(dev.postmark.model.StampIdentity.key(EXHIBITION,"visitor","minecraft:grass_block"))).findFirst().orElseThrow();
                var expert=session.album().stamps().stream().filter(s->s.key().equals(dev.postmark.model.StampIdentity.key(EXHIBITION,"expert","minecraft:grass_block"))).findFirst().orElseThrow();
                if(!definition.name().equals("联动花园 · 普通章") || !expert.name().equals("联动花园 · 大师章")) throw new AssertionError("Server exhibition name was not captured");
                if(definition.expert() || !expert.expert()) throw new AssertionError("visitor/expert style distinction failed");
                if(!definition.asset().equals(expert.asset())) throw new AssertionError("Handle style must not alter the artwork");
                if(session.store().load().stamps().stream().noneMatch(s->s.key().equals(expert.key()) && s.expert())) throw new AssertionError("Expert style lost after reload");
                var grass=session.store().image(definition.asset()); int opaque=0;
                for(int y=0;y<grass.getHeight();y++) for(int x=0;x<grass.getWidth();x++) if((grass.getRGB(x,y)>>>24)>0) opaque++;
                if(opaque<grass.getWidth()*grass.getHeight()/8 || opaque>=grass.getWidth()*grass.getHeight()) throw new AssertionError("3D grass block snapshot has invalid coverage "+opaque);
                var card=session.album().selected().stamp(definition.key(),definition.asset(),.32,.51,.16,0)
                        .stamp(definition.key(),definition.asset(),.59,.58,.16,.18);
                session.update(session.album().replace(card));
                mc.setScreen(new PostcardScreen(null,session,definition.key()));
                Path samples=mc.gameDirectory.toPath().resolve("smoke-shots"); Files.createDirectories(samples);
                javax.imageio.ImageIO.write(grass,"PNG",samples.resolve("native-grass.png").toFile());
                javax.imageio.ImageIO.write(paper,"PNG",samples.resolve("native-paper.png").toFile());
                var postcard=dev.postmark.render.PostcardPainter.paint(card,session.store()::image);
                javax.imageio.ImageIO.write(postcard,"PNG",samples.resolve("native-postcard.png").toFile());
                next();
            } else if(stage==6 && ticks-stageAt==10) {
                shot="04-signmeup-counter.png";
                work=mc.getSingleplayerServer().submit(()->EPServer.updateMetadata(EXHIBITION,m->m.withName("更新花园")));
            } else if(stage==6 && ticks-stageAt>70 && work.isDone()) {
                var definitions=ClientSession.get().album().stamps().stream().filter(s->s.key().startsWith(EXHIBITION.toString())).toList();
                if(definitions.size()!=2 || definitions.stream().anyMatch(s->!s.name().startsWith("更新花园"))) throw new AssertionError("Metadata-only rename did not refresh stored names");
                if(ClientSession.get().store().load().stamps().stream().filter(s->s.key().startsWith(EXHIBITION.toString())).anyMatch(s->!s.name().startsWith("更新花园"))) throw new AssertionError("Updated names not persisted");
                prepareCounter("visitor","minecraft:paper");next();
            } else if(stage==7 && ticks-stageAt>30 && work.isDone()) {
                work.join();clickCounter(secondCounter);next();
            } else if(stage==8 && ticks-stageAt>60) {
                assertVariant("visitor","minecraft:paper",3);
                if(!animationSeen)throw new AssertionError("New stamp animation never appeared");
                clickCounter(counter);next();
            } else if(stage==9 && ticks-stageAt>60) {
                assertVariant("visitor","minecraft:grass_block",3);prepareCounter("expert","exhibition_portal:textures/test_stamp.png");next();
            } else if(stage==10 && ticks-stageAt>30 && work.isDone()) {
                work.join();clickCounter(secondCounter);next();
            } else if(stage==11 && ticks-stageAt>60) {
                assertVariant("expert","exhibition_portal:textures/test_stamp.png",4);
                if(Boolean.getBoolean("postmark.anvilStampSmoke")) {prepareCounter("visitor","minecraft:nether_star");next();}
                else finish();
            } else if(stage==12 && ticks-stageAt>30 && work.isDone()) {
                work.join();animationSeen=false;clickCounter(secondCounter);next();
            } else if(stage==13 && ticks-stageAt>80) {
                assertVariant("visitor","minecraft:nether_star",5);
                var session=ClientSession.get();var stamp=session.album().stamps().stream().filter(t->t.key().equals(dev.postmark.model.StampIdentity.key(EXHIBITION,"visitor","minecraft:nether_star"))).findFirst().orElseThrow();
                var image=session.store().image(stamp.asset());
                javax.imageio.ImageIO.write(image,"PNG",mc.gameDirectory.toPath().resolve("smoke-shots/anvil-celestial.png").toFile());
                if(!animationSeen)throw new AssertionError("Oversized model acquisition animation missing");
                int opaque=0;for(int py=0;py<image.getHeight();py++)for(int px=0;px<image.getWidth();px++) {
                    int alpha=image.getRGB(px,py)>>>24;if(alpha!=0)opaque++;
                    if((px==0 || py==0 || px==image.getWidth()-1 || py==image.getHeight()-1) && alpha!=0)throw new AssertionError("Large model clipped on image boundary");
                }
                if(opaque<20)throw new AssertionError("Large model capture is empty");
                prepareCounter("visitor","minecraft:recovery_compass");next();
            } else if(stage==14 && ticks-stageAt>30 && work.isDone()) {
                work.join();clickCounter(secondCounter);next();
            } else if(stage==15 && ticks-stageAt>65) {
                assertVariant("visitor","minecraft:recovery_compass",6);
                prepareCounter("visitor","minecraft:nether_star");next();
            } else if(stage==16 && ticks-stageAt>30 && work.isDone()) {
                work.join();clickCounter(secondCounter);next();
            } else if(stage==17 && ticks-stageAt>65) {
                assertVariant("visitor","minecraft:nether_star",6);
                if(!dev.postmark.render.CounterCollectionMarker.collected(mc.level.getBlockEntity(counter)) || !dev.postmark.render.CounterCollectionMarker.collected(mc.level.getBlockEntity(secondCounter)))throw new AssertionError("Collected counters missing marker");
                mc.setScreen(null);
                mc.player.setYRot(145);mc.player.setXRot(8);
                work=mc.getSingleplayerServer().submit(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().teleportTo(counter.getX()+3,counter.getY()+1,counter.getZ()+4));next();
            } else if(stage==18 && ticks-stageAt>25) {
                var renderer=new org.teacon.exhibition_portal.client.interaction.StampingCounterBlockEntityRenderer(null);
                var state=renderer.createRenderState();renderer.extractRenderState((StampingCounterBlockEntity)mc.level.getBlockEntity(secondCounter),state,0,mc.player.position(),null);
                if(!((dev.postmark.compat.CounterCollectionState)state).postmark$collected())throw new AssertionError("Marker render state missing collection flag");
                shot="counter-collected-near.png";
                next();
            } else if(stage==19 && ticks-stageAt>15) {
                work=mc.getSingleplayerServer().submit(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().teleportTo(counter.getX()+10,counter.getY()+1,counter.getZ()+15));next();
            } else if(stage==20 && ticks-stageAt>25) {
                shot="counter-collected-far.png";next();
            } else if(stage==21 && ticks-stageAt>15) {
                work=mc.getSingleplayerServer().submit(()->EPServer.updateFootprint(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst(),EXHIBITION,f->new org.teacon.exhibition_portal.components.ExhibitionFootprint(f.uuid(),f.mark(),java.util.List.of())));next();
            } else if(stage==22 && ticks-stageAt>45) {
                work.join();
                if(dev.postmark.render.CounterCollectionMarker.collected(mc.level.getBlockEntity(counter)) || dev.postmark.render.CounterCollectionMarker.collected(mc.level.getBlockEntity(secondCounter)))throw new AssertionError("Reset left collected counter markers behind");
                work=mc.getSingleplayerServer().submit(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().teleportTo(counter.getX()-1,counter.getY(),counter.getZ()));next();
            } else if(stage==23 && ticks-stageAt>20) {
                mc.options.keyShift.setDown(true);try{clickCounter(counter);}finally{mc.options.keyShift.setDown(false);}next();
            } else if(stage==24 && ticks-stageAt>40) {
                if(mc.screen!=null)throw new AssertionError("Shift counter opened a screen");
                if(ClientSession.get().stampInbox().size()!=1)throw new AssertionError("Shift acquisition was not queued");
                prepareCounter("visitor","minecraft:paper");next();
            } else if(stage==25 && ticks-stageAt>30 && work.isDone()) {
                work.join();mc.options.keyShift.setDown(true);try{clickCounter(secondCounter);}finally{mc.options.keyShift.setDown(false);}next();
            } else if(stage==26 && ticks-stageAt>40) {
                if(mc.screen!=null || ClientSession.get().stampInbox().size()!=2)throw new AssertionError("Second Shift acquisition did not stay silent and queued");
                mc.options.keyShift.setDown(true);try{clickCounter(secondCounter);}finally{mc.options.keyShift.setDown(false);}next();
            } else if(stage==27 && ticks-stageAt>40) {
                if(ClientSession.get().stampInbox().size()!=2)throw new AssertionError("Repeated counter duplicated queued animation");
                ClientSession.clear();if(ClientSession.get().stampInbox().size()!=2)throw new AssertionError("Saved animation queue lost after session reload");
                PostcardScreen.show(null,null);next();
            } else if(stage==28 && ticks-stageAt>17) {
                var field=PostcardScreen.class.getDeclaredField("inboxAnimation");field.setAccessible(true);
                var animation=(dev.postmark.client.StampInboxAnimation)field.get(mc.screen);
                if(animation==null||animation.stamps().size()!=2)throw new AssertionError("Queued stamps did not form one batch");
                shot="silent-batch-inbox.png";next();
            } else if(stage==29 && ticks-stageAt>60) {
                if(!ClientSession.get().stampInbox().isEmpty())throw new AssertionError("Completed batch was not acknowledged");
                PostcardScreen.show(null,null);next();
            } else if(stage==30 && ticks-stageAt>15) {
                var field=PostcardScreen.class.getDeclaredField("inboxAnimation");field.setAccessible(true);
                if(field.get(mc.screen)!=null)throw new AssertionError("Batch replayed on next opening");finish();
            }        } catch(Throwable error) {
            Postmark.LOGGER.error("COMPAT SMOKE FAILED stage="+stage,error);
            try { Files.writeString(mc.gameDirectory.toPath().resolve("compat-result.txt"),"FAIL at stage "+stage+": "+error); } catch(Exception ignored) {}
            mc.stop(); stage=99;
        }
    }
    private static void finish() throws Exception {
        var mc=Minecraft.getInstance();
                Files.writeString(mc.gameDirectory.toPath().resolve("compat-result.txt"),
                        "PASS: Shift real counter acquisitions stay in world, persist a deduplicated batch across session reload, then animate and acknowledge only after playback; official pinned AnvilCraft celestial oversized GUI model captured fully with transparent edges and spacetime model alternates correctly (vanilla registry aliases); acquisition animation appears for new artwork only; collected counter render state and reset removal verified; real clicks on two visitor counters and two expert artworks independently collect, persist and select the right tool; revisiting the first visitor preserves both; real SignMeUp 1.1.12 counter -> Postmark screen; server grants stamp; client waits for confirmation; map location and size synchronized and persisted by server; native grass_block GUI model, native paper GUI item, and direct PNG all captured; alpha preserved; repeated native item imprints exported; actual server visitor and expert records show different handles with identical artwork and survive reload; real exhibition names captured; metadata-only server rename updates both existing stamp names and persists.");
                mc.stop(); stage=99;
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if(shot==null) return; String name=shot; shot=null; var mc=Minecraft.getInstance();
        Screenshot.takeScreenshot(mc.getMainRenderTarget(),image->{
            try(image) {
                Path path=mc.gameDirectory.toPath().resolve("smoke-shots"); Files.createDirectories(path); image.writeToFile(path.resolve(name));
            } catch(Exception e) { Postmark.LOGGER.error("Compatibility screenshot failed",e); }
        });
    }
}
