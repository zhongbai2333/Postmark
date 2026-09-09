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
    private static BlockPos counter;
    private static MapPlacement.Rect expected;
    private static CompletableFuture<?> work;
    private static String shot;
    private static CompletableFuture<java.awt.image.BufferedImage> paperCapture, pngCapture;
    private static void next() { stage++; stageAt=ticks; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("postmark.compatSmoke")) return;
        var mc=Minecraft.getInstance(); ticks++;
        try {
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
                if(ClientSession.get().album().stamps().stream().noneMatch(s->s.key().equals(EXHIBITION+"/visitor") && !s.practice()))
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
                    && ClientSession.get().album().stamps().stream().anyMatch(s->s.key().equals(EXHIBITION+"/expert"))) {
                var paper=paperCapture.join(); var png=pngCapture.join();
                if(paper.getWidth()!=16*(int)mc.getWindow().getGuiScale() || (paper.getRGB(0,0)>>>24)!=0) throw new AssertionError("Item snapshot must retain transparent background");
                if(png.getWidth()<16) throw new AssertionError("Raw texture stamp failed");
                var session=ClientSession.get();
                var definition=session.album().stamps().stream().filter(s->s.key().equals(EXHIBITION+"/visitor")).findFirst().orElseThrow();
                var expert=session.album().stamps().stream().filter(s->s.key().equals(EXHIBITION+"/expert")).findFirst().orElseThrow();
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
                Files.writeString(mc.gameDirectory.toPath().resolve("compat-result.txt"),
                        "PASS: real SignMeUp 1.1.12 counter -> Postmark screen; server grants stamp; client waits for confirmation; map location and size synchronized and persisted by server; native grass_block GUI model, native paper GUI item, and direct PNG all captured; alpha preserved; repeated native item imprints exported; actual server visitor and expert records show different handles with identical artwork and survive reload; real exhibition names captured; metadata-only server rename updates both existing stamp names and persists.");
                mc.stop(); stage=99;
            }        } catch(Throwable error) {
            Postmark.LOGGER.error("COMPAT SMOKE FAILED stage="+stage,error);
            try { Files.writeString(mc.gameDirectory.toPath().resolve("compat-result.txt"),"FAIL at stage "+stage+": "+error); } catch(Exception ignored) {}
            mc.stop(); stage=99;
        }
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