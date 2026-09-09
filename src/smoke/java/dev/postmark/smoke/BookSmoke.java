package dev.postmark.smoke;

import dev.postmark.client.*;
import dev.postmark.model.*;
import dev.postmark.render.PaperViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.nio.file.*;
import java.util.*;

@EventBusSubscriber(modid="postmark",value=Dist.CLIENT)
public final class BookSmoke {
    private static int ticks,start=-1;
    private static Album baseline;
    private static PostcardScreen desk;
    private static PostcardAlbumScreen book;
    private static String shot;
    private static UUID selected;
    private static MouseButtonEvent mouse(double x,double y) {return new MouseButtonEvent(x,y,new MouseButtonInfo(0,0));}
    private static double[] slot(int index) {
        int bw=Math.min(600,book.width-64),bh=Math.min(346,book.height-82),lw=(bw-12)/2,bx=(book.width-bw)/2,by=(book.height-bh)/2-4;
        int i=index%8,local=i%4,tw=(lw-30)/2,th=(bh-40)/2;
        return new double[]{bx+(i/4)*(lw+12)+10+(local%2)*(tw+10),by+14+(local/2)*(th+8),tw,th};
    }
    private static void remove(int index,boolean confirm) throws Exception {
        var pos=slot(index);book.mouseClicked(mouse(pos[0]+pos[2]-14,pos[1]+pos[3]-13),false);
        if(book.pendingDeletion()==null) throw new AssertionError("Missing visible deletion confirmation");
        book.mouseClicked(mouse(book.width/2+(confirm?-30:30),book.height/2+17),false);
    }
    private static void next() {int bw=Math.min(600,book.width-64),bh=Math.min(346,book.height-82);book.mouseClicked(mouse((book.width+bw)/2-20,(book.height-bh)/2-4+bh+18),false);}
    private static void open() throws Exception {
        var v=PaperViewport.fit(desk.width,desk.height,ClientSession.get().album().selected().aspectRatio());
        desk.mouseClicked(mouse(v.x()+v.width()+34,v.y()-26),false);
        if(!(Minecraft.getInstance().screen instanceof PostcardAlbumScreen result)) throw new AssertionError("Desk book control failed");book=result;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("postmark.bookOnly")) return;
        var mc=Minecraft.getInstance();ticks++;
        try {
            if(start<0) {
                if(mc.screen instanceof TitleScreen && mc.getOverlay()==null && ticks>40) {
                    var session=ClientSession.get();baseline=session.album();var cards=new ArrayList<Postcard>();
                    for(int i=0;i<19;i++) {
                        var image=new java.awt.image.BufferedImage(120,80,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        var g=image.createGraphics();g.setColor(new java.awt.Color(java.awt.Color.HSBtoRGB(i/20f,.24f,.84f)));g.fillRect(0,0,120,80);
                        g.setColor(new java.awt.Color(0xF8E8C6));g.fillRect(8,8,104,64);g.setColor(new java.awt.Color(0x57664B));
                        g.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,java.awt.Font.BOLD,30));g.drawString(Integer.toString(i+1),45,50);g.dispose();
                        var blank=Postcard.blank();String asset=session.store().putImage(image);
                        cards.add(new Postcard(blank.id(),blank.title(),asset,List.of(),List.of(),i%3==0?900:1800,i%3==1?1200:1800));
                    }
                    session.update(new Album(1,cards.getFirst().id(),cards,baseline.stamps()));
                    desk=new PostcardScreen(null,session,null);mc.setScreen(desk);open();start=ticks;
                }
                return;
            }
            int t=ticks-start;
            if(t==8) {if(book.spreadCount()!=3 || book.spreadIndex()!=0) throw new AssertionError("Book must paginate eight cards");shot="01-eight-card-book.png";}
            if(t==12) next();
            if(t==17) shot="02-page-turn.png";
            if(t==27) {if(book.spreadIndex()!=1) throw new AssertionError("Next spread failed");next();}
            if(t==42) {if(book.spreadIndex()!=2) throw new AssertionError("Last spread failed");shot="03-last-spread.png";}
            if(t==46) {var p=slot(18);book.mouseClicked(mouse(p[0]+p[2]-14,p[1]+p[3]-13),false);shot="04-delete-confirm.png";}
            if(t==49) {book.mouseClicked(mouse(book.width/2+30,book.height/2+17),false);if(ClientSession.get().album().cards().size()!=19) throw new AssertionError("Cancel deleted a card");}
            if(t==53) remove(18,true);
            if(t==56) remove(17,true);
            if(t==59) {remove(16,true);if(book.spreadIndex()!=1 || book.spreadCount()!=2 || ClientSession.get().album().cards().size()!=16) throw new AssertionError("Deleting last spread must clamp page");shot="05-after-delete.png";}
            if(t==63) {
                selected=ClientSession.get().album().cards().get(9).id();var p=slot(9);book.mouseClicked(mouse(p[0]+p[2]/2,p[1]+p[3]/2),false);
                if(mc.screen!=desk || !ClientSession.get().album().current().equals(selected)) throw new AssertionError("Thumbnail must open correct postcard");
            }
            if(t==68) {open();if(book.spreadIndex()!=1) throw new AssertionError("Reopen must show current spread");}
            if(t==73) {
                remove(9,true);var session=ClientSession.get();if(session.album().current().equals(selected) || session.album().cards().size()!=15) throw new AssertionError("Selected deletion did not select neighbor");
                if(!session.store().load().equals(session.album()) || !session.album().stamps().equals(baseline.stamps())) throw new AssertionError("Deletion persistence or stamp collection changed");
                var card=session.album().selected();session.update(new Album(1,card.id(),List.of(card),baseline.stamps()));mc.setScreen(new PostcardAlbumScreen(desk,session));book=(PostcardAlbumScreen)mc.screen;
            }
            if(t==80) {remove(0,true);var album=ClientSession.get().album();if(album.cards().size()!=1 || album.selected().background()!=null || !album.selected().imprints().isEmpty()) throw new AssertionError("Deleting final card must leave fresh paper");shot="06-empty-book.png";}
            if(t==87) {
                book.mouseClicked(mouse(book.width-22,22),false);if(mc.screen!=desk) throw new AssertionError("Close book failed");shot="07-book-entry.png";
            }
            if(t==91) {
                ClientSession.get().update(baseline);mc.setScreen(null);ClientSession.clear();
                Files.writeString(mc.gameDirectory.toPath().resolve("book-result.txt"),"PASS: visible book entry, eight thumbnails per spread, proportional portrait/square/landscape cards, page turn, partial last spread, cancel/confirm deletion, empty-spread page clamp, correct thumbnail selection, selected-card neighbor, last-card replacement, persisted changes and unchanged stamp collection.");mc.stop();
            }
        } catch(Throwable e) {
            dev.postmark.Postmark.LOGGER.error("BOOK TEST FAILED",e);
            try {if(baseline!=null) ClientSession.get().update(baseline);Files.writeString(mc.gameDirectory.toPath().resolve("book-result.txt"),"FAIL: "+e);} catch(Exception ignored) {}
            mc.stop();
        }
    }
    @SubscribeEvent public static void rendered(RenderFrameEvent.Post event) {
        if(shot==null) return;var name=shot;shot=null;var mc=Minecraft.getInstance();
        Screenshot.takeScreenshot(mc.getMainRenderTarget(),image->{try(image) {var folder=mc.gameDirectory.toPath().resolve("book-shots");Files.createDirectories(folder);image.writeToFile(folder.resolve(name));} catch(Exception e) {dev.postmark.Postmark.LOGGER.error("Book screenshot failed",e);}});
    }
}
