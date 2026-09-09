package dev.postmark.smoke;

import dev.postmark.client.*;
import dev.postmark.model.*;
import dev.postmark.render.PaperViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.nio.file.*;
import java.util.*;

/** Compare actual GPU silhouettes, without the hand-held tool covering the landing ghost. */
@EventBusSubscriber(modid="postmark",value=Dist.CLIENT)
public final class AlignmentSmoke {
    private static Harness harness;
    private static int ticks,stage;
    private static boolean shot,pending;
    private static BitSet preview;
    private static Runnable advance;
    private static Album baseline;
    private static final double[][] CASES={
        {1800,1200,.4317,.5173,.1637,0,0}, {1800,1200,.4317,.5173,.1637,.37,1},
        {900,1800,.4531,.5227,.217,1.13,1}, {1800,450,.7211,.4179,.0937,-.52,1},
        {1800,1800,.0153,.0337,.329,Math.PI/4,0}, {1800,1200,.983,.967,.0237,Math.PI/2,1}
    };
    private static Object call(Object target,String name,Class<?>[] types,Object...args) throws Exception {
        var method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);
    }
    private static void set(Object target,String name,Object value) throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass()) try {
            var field=type.getDeclaredField(name);field.setAccessible(true);field.set(target,value);return;
        } catch(NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }
    private static final class Harness extends Screen {
        private PostcardScreen delegate;
        private ClientSession session;
        private String ink;
        private boolean committed;
        Harness() {super(Component.literal("Pixel alignment verification"));}
        @Override protected void init() {
            try {
                session=ClientSession.get();baseline=session.album();
                var image=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g=image.createGraphics();g.setColor(java.awt.Color.MAGENTA);g.fillRect(7,3,29,47);g.fillRect(40,12,19,11);g.dispose();
                ink=session.store().putImage(image);prepare();
            } catch(Exception e) {fail(e);}
        }
        void prepare() throws Exception {
            if(delegate!=null) delegate.removed();
            double[] c=CASES[stage];var album=Album.empty().unlock(new StampDefinition("alignment/visitor","test",ink,false));
            var blank=album.selected();session.update(album.replace(new Postcard(blank.id(),blank.title(),null,List.of(),List.of(),(int)c[0],(int)c[1])));
            delegate=new PostcardScreen(null,session,null);
            set(delegate,"minecraft",minecraft);set(delegate,"font",font);delegate.width=width-(int)c[6];delegate.height=height-(int)c[6];
            call(delegate,"init",new Class<?>[]{});
            var v=PaperViewport.fit(delegate.width,delegate.height,session.album().selected().aspectRatio());
            set(delegate,"toolX",v.x()+v.width()*c[2]);set(delegate,"toolY",v.y()+v.height()*c[3]);set(delegate,"toolSize",c[4]);set(delegate,"angle",c[5]);
            committed=false;
        }
        void commit() throws Exception {
            double[] c=CASES[stage];session.update(session.album().replace(session.album().selected().stamp("alignment/visitor",ink,c[2],c[3],c[4],c[5])));
            call(delegate,"refresh",new Class<?>[]{});committed=true;
        }
        @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float t) {}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float t) {
            g.fill(0,0,width,height,0xff101010);
            try {
                var v=PaperViewport.fit(delegate.width,delegate.height,session.album().selected().aspectRatio());
                call(delegate,"drawPaper",new Class<?>[]{GuiGraphicsExtractor.class,int.class,int.class},g,v.x(),v.y());
                if(!committed) call(delegate,"drawLandingPreview",new Class<?>[]{GuiGraphicsExtractor.class,float.class},g,1f);
            } catch(Exception e) {fail(e);}
        }
        @Override public boolean isPauseScreen() {return false;}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("postmark.alignmentOnly")) return;
        var mc=Minecraft.getInstance();
        if(harness==null) {
            if(mc.screen instanceof TitleScreen && mc.getOverlay()==null && ++ticks>40) {harness=new Harness();mc.setScreen(harness);ticks=0;}
            return;
        }
        if(advance!=null) {var task=advance;advance=null;task.run();ticks=0;return;}
        if(!pending && ++ticks>=4) {ticks=0;shot=true;pending=true;}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if(!shot) return;shot=false;var mc=Minecraft.getInstance();
        Screenshot.takeScreenshot(mc.getMainRenderTarget(),nativeImage->{
            try(nativeImage) {
                var folder=mc.gameDirectory.toPath().resolve("alignment-shots");Files.createDirectories(folder);
                var path=folder.resolve(stage+(harness.committed?"-actual.png":"-preview.png"));nativeImage.writeToFile(path);
                var image=javax.imageio.ImageIO.read(path.toFile());var mask=new BitSet(image.getWidth()*image.getHeight());
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int rgb=image.getRGB(x,y),r=(rgb>>>16)&255,g=(rgb>>>8)&255,b=rgb&255;
                    if(r-g>65 && b-g>65) mask.set(y*image.getWidth()+x);
                }
                if(mask.isEmpty()) throw new AssertionError("No ink pixels in "+path);
                if(!harness.committed) {preview=mask;advance=()->{try {harness.commit();pending=false;} catch(Exception e) {fail(e);}};}
                else {
                    var difference=(BitSet)preview.clone();difference.xor(mask);
                    if(!difference.isEmpty()) throw new AssertionError("Case "+stage+" has "+difference.cardinality()+" mismatched pixels; preview="+preview.cardinality()+", actual="+mask.cardinality());
                    advance=()->{try {
                    if(++stage==CASES.length) {
                        harness.delegate.removed();ClientSession.get().update(baseline);
                        Files.writeString(mc.gameDirectory.toPath().resolve("alignment-result.txt"),"PASS: all six GPU preview/committed silhouette comparisons have zero mismatched pixels, including odd viewport dimensions, portrait, panorama, rotation, odd stamp sizes and clipped edges.");
                        mc.stop();return;
                    }
                    harness.prepare();pending=false;
                    } catch(Exception e) {fail(e);}};
                }
            } catch(Throwable e) {fail(e);}
        });
    }
    private static void fail(Throwable e) {
        dev.postmark.Postmark.LOGGER.error("ALIGNMENT FAILED",e);
        try {Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("alignment-result.txt"),"FAIL: "+e);if(baseline!=null) ClientSession.get().update(baseline);} catch(Exception ignored) {}
        Minecraft.getInstance().stop();
    }
}
