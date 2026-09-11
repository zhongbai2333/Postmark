package dev.postmark.compatsmoke;

import dev.postmark.client.*;
import dev.postmark.compat.GuideBridge;
import dev.postmark.model.*;
import dev.postmark.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.teacon.exhibition_portal.components.EPServer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid="postmark",value=Dist.CLIENT)
public final class VenueStickerSmoke {
    private static final UUID VENUE=new UUID(0,1);
    private static int ticks,stage,at;
    private static CompletableFuture<?> work;
    private static TravelGuideScreen guide;
    private static PostcardScreen desk;
    private static List<StampDefinition> collection;
    private static String asset,shot;
    private static void next(){stage++;at=ticks;}
    private static MouseButtonEvent mouse(double x,double y,int button){return new MouseButtonEvent(x,y,new MouseButtonInfo(button,0));}
    private static void click(double x,double y){var screen=Minecraft.getInstance().screen;screen.mouseClicked(mouse(x,y,0),false);if(Minecraft.getInstance().screen==screen)screen.mouseReleased(mouse(x,y,0));}
    private static double[] point(){var v=PaperViewport.fit(desk.width,desk.height,ClientSessionSafe.aspect());return new double[]{v.x()+v.width()*.55,v.y()+v.height()*.55};}
    private static final class ClientSessionSafe {static double aspect(){try{return ClientSession.get().album().selected().aspectRatio();}catch(Exception e){throw new RuntimeException(e);}}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("postmark.venueStickerSmoke"))return;
        var mc=Minecraft.getInstance();ticks++;
        try {
            if(ticks>1600)throw new AssertionError("Sticker smoke timeout at "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null&&ticks>40){
                next();mc.createWorldOpenFlows().createFreshLevel("Postmark-sticker-"+System.currentTimeMillis(),new LevelSettings("Venue sticker fixture",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),new WorldOptions(1880L,false,false),WorldPresets::createFlatWorldDimensions,new TitleScreen());
            }else if(stage==1&&mc.player!=null&&mc.screen==null){
                work=mc.getSingleplayerServer().submit(()->EPServer.updateMetadata(VENUE,m->m.withName("苔原花园")));next();
            }else if(stage==2&&work.isDone()&&!GuideBridge.venues().isEmpty()){
                work.join();collection=List.copyOf(ClientSession.get().album().stamps());TravelGuideScreen.show(null);guide=(TravelGuideScreen)mc.screen;next();
            }else if(stage==3&&ticks-at>15){
                var p=guide.captionCenter(VENUE);click(p[0],p[1]);if(!VENUE.equals(guide.detailVenue()))throw new AssertionError("Venue details did not open");shot="sticker-entry.png";next();
            }else if(stage==4&&ticks-at>8){var p=guide.stickerButtonCenter();click(p[0],p[1]);next();
            }else if(stage==5&&mc.screen instanceof PostcardScreen screen){
                desk=screen;var field=PostcardScreen.class.getDeclaredField("tool");field.setAccessible(true);var tool=(StampDefinition)field.get(desk);
                if(tool==null||!tool.key().equals("postmark:venue/"+VENUE))throw new AssertionError("Sticker button selected wrong tool");asset=tool.asset();
                var session=ClientSession.get();var image=session.store().image(asset);int size=image.getWidth()/2-48,ink=0;
                for(int y=(20+size+9)*2;y<(20+size+18)*2;y++)for(int x=10;x<image.getWidth()-10;x++)if((image.getRGB(x,y)&0xFFFFFF)==0x443B2D)ink++;
                if(ink<12)throw new AssertionError("Native Chinese caption missing: "+ink);
                try(var stream=mc.getResourceManager().open(net.minecraft.resources.Identifier.parse(GuideBridge.venues().getFirst().icon()))) {
                    var icon=javax.imageio.ImageIO.read(stream);var expected=VenueStickerPainter.paint(icon,null,size,0);
                    for(int y=40;y<(20+size)*2;y++)for(int x=40;x<(20+size)*2;x++)if(image.getRGB(x,y)!=expected.getRGB(x,y))throw new AssertionError("Sticker image contains overlays or was altered");
                }
                var folder=mc.gameDirectory.toPath().resolve("sticker-shots");Files.createDirectories(folder);javax.imageio.ImageIO.write(image,"PNG",folder.resolve("venue-sticker-asset.png").toFile());
                if(!session.album().selected().imprints().isEmpty())throw new AssertionError("Opening sticker auto-placed imprint");
                var p=point();desk.mouseMoved(p[0],p[1]);desk.mouseScrolled(p[0],p[1],0,2);
                desk.mouseClicked(mouse(p[0],p[1],1),false);desk.mouseDragged(mouse(p[0]+22,p[1]-14,1),22,-14);desk.mouseReleased(mouse(p[0]+22,p[1]-14,1));
                next();
            }else if(stage==6&&ticks-at>12){shot="sticker-preview.png";var p=point();click(p[0],p[1]);next();
            }else if(stage==7&&ticks-at>16){
                var session=ClientSession.get();var card=session.album().selected();if(card.imprints().size()!=1)throw new AssertionError("Sticker was not placed exactly once");
                var imprint=card.imprints().getFirst();if(!imprint.asset().equals(asset)||imprint.size()<=.34||Math.abs(imprint.angle())<.03)throw new AssertionError("Sticker resize/rotation was lost");
                if(!session.album().stamps().equals(collection)||!session.store().load().equals(session.album()))throw new AssertionError("Sticker changed stamp collection or did not persist");
                javax.imageio.ImageIO.write(PostcardPainter.paint(card,session.store()::image),"PNG",mc.gameDirectory.toPath().resolve("sticker-shots/venue-postcard-export.png").toFile());shot="sticker-postcard.png";next();
            }else if(stage==8&&ticks-at>10){
                ClientSession.clear();var session=ClientSession.get();if(!session.album().selected().imprints().getFirst().asset().equals(asset))throw new AssertionError("Reopen lost sticker");
                desk=new PostcardScreen(null,session,null);mc.setScreen(desk);
                var v=PaperViewport.fit(desk.width,desk.height,session.album().selected().aspectRatio());
                click(v.x()-34,v.y()+v.height()-12);var p=point();click(p[0],p[1]);
                if(!session.store().load().selected().imprints().isEmpty()||!session.album().stamps().equals(collection))throw new AssertionError("Sticker erase affected collection");
                Files.writeString(mc.gameDirectory.toPath().resolve("sticker-result.txt"),"PASS: actual guide caption -> visible sticker button -> postcard pickup; native Chinese font captured; whole venue icon pixel-matches with no corner stamps/footprint/check; no automatic placement; mouse rotate/resize/place one imprint; saved artwork exports and survives reload; mouse erasure preserves collection and progress.");mc.stop();stage=99;
            }
        }catch(Throwable e){dev.postmark.Postmark.LOGGER.error("STICKER SMOKE FAILED stage="+stage,e);try{Files.writeString(mc.gameDirectory.toPath().resolve("sticker-result.txt"),"FAIL stage="+stage+": "+e);}catch(Exception ignored){}mc.stop();stage=99;}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){if(shot==null)return;String name=shot;shot=null;var mc=Minecraft.getInstance();Screenshot.takeScreenshot(mc.getMainRenderTarget(),image->{try(image){var folder=mc.gameDirectory.toPath().resolve("sticker-shots");Files.createDirectories(folder);image.writeToFile(folder.resolve(name));}catch(Exception e){dev.postmark.Postmark.LOGGER.error("Sticker screenshot failed",e);}});}
}
