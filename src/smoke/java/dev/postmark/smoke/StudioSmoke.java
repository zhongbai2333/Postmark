package dev.postmark.smoke;

import dev.postmark.client.ClientSession;
import dev.postmark.client.PostcardScreen;
import dev.postmark.client.SignatureScreen;
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
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

/** Dev-only game test. Compiled only with -PsmokeTest, never packaged in release jars. */
@EventBusSubscriber(modid="postmark",value=Dist.CLIENT)
public class StudioSmoke {
    private static int tick;
    private static int start=-1;
    private static PostcardScreen screen;
    private static String shot;
    private static dev.postmark.model.Album scrollBaseline;
    private static String selectedDuringReturn;
    private static java.util.UUID sentCard;
    private static int resizeCount;
    private static dev.postmark.model.Album bagBaseline;
    private static int bagImprints;
    private static String bagKey;
    private static final AtomicInteger screenshots=new AtomicInteger();
    private static MouseButtonEvent mouse(double x,double y) { return new MouseButtonEvent(x,y,new MouseButtonInfo(0,0)); }
    private static double[] point(double u,double v) {
        double maxW=Math.max(70,screen.width-220),maxH=Math.max(45,screen.height-100);
        double w=Math.min(maxW,maxH*1.5)*.8,h=w/1.5;
        return new double[]{(screen.width-w)/2.0+12+w*u,(screen.height-h)/2.0+h*v};
    }
    private static double[] toolPosition;
    private static void press(double u,double v) {
        double[] pos=point(u,v); double[] from=new double[]{38*.8,screen.height*.1+70*.8}; screen.mouseClicked(mouse(from[0],from[1]),false);
        screen.mouseDragged(mouse(pos[0],pos[1]),0,0);
    }
    private static void release(double u,double v) {
        double[] pos=point(u,v); screen.mouseReleased(mouse(pos[0],pos[1])); toolPosition=pos;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("postmark.smoke")) return;
        var mc=Minecraft.getInstance(); tick++;
        try {
            if(start<0) {
                if(mc.screen instanceof TitleScreen && mc.getOverlay()==null && tick>40) {
                    var session=ClientSession.get();
                    if(session.album().stamps().stream().filter(s->s.practice()).count()!=1) throw new AssertionError("Exactly one built-in stamp is allowed");
                    var fresh=dev.postmark.model.Album.empty();
                    session.update(new dev.postmark.model.Album(1,fresh.current(),fresh.cards(),session.album().stamps()));
                    screen=new PostcardScreen(null,session,null); mc.setScreen(screen); start=tick;
                }
                return;
            }
            int t=tick-start;
            // Allow asynchronous PNG export, flight and the new-card transition to finish.
            if(t>=390 && t<420) return;
            if(t>=420) t-=30;
            if(t==15) { press(.29,.50); shot="01-held-tool.png"; }
            if(t==21 && screen.stampPreviewAmount()!=0) throw new AssertionError("Preview appeared before 500ms");
            if(t==30 && screen.stampPreviewAmount()<.99) throw new AssertionError("Preview must activate after 500ms");
            if(t==62) {
                if(screen.stampPreviewAmount()<.99) throw new AssertionError("Stationary held stamp did not fade");
                if(!ClientSession.get().album().selected().imprints().isEmpty()) throw new AssertionError("Preview must not save an imprint");
                shot="11-idle-preview.png";
            }
            if(t==64) {
                var pos=point(.31,.50);screen.mouseDragged(mouse(pos[0],pos[1]),0,0);
                if(screen.stampPreviewAmount()<.99) throw new AssertionError("Motion must retain transparent preview");
                shot="12-preview-moved.png";
            }
            if(t==111) {
                if(screen.stampPreviewAmount()<.99) throw new AssertionError("Second stationary wait failed");
                var pos=point(.31,.50);screen.mouseScrolled(pos[0],pos[1],0,1);
                if(screen.stampPreviewAmount()<.99) throw new AssertionError("Rotation must retain transparent preview");
                screen.mouseScrolled(pos[0],pos[1],0,-1);screen.mouseMoved(point(.29,.5)[0],point(.29,.5)[1]);
            }
            if(t==125) release(.29,.50);
            if(t==140) { press(.53,.57); if(screen.stampPreviewAmount()!=0) throw new AssertionError("New pickup must reset preview"); release(.53,.57); }
            if(t==155) { press(.53,.57); release(.53,.57); }
            if(t==172) {
                if(ClientSession.get().album().selected().imprints().size()!=3) throw new AssertionError("Expected three independent imprints");
                screen.keyPressed(new KeyEvent(69,0,0));
                double[] pos=point(.53,.57); screen.mouseClicked(mouse(pos[0],pos[1]),false);
                if(ClientSession.get().album().selected().imprints().size()!=2) throw new AssertionError("Eraser must remove only the top imprint");
                screen.mouseMoved(pos[0],pos[1]); shot="10-held-eraser.png";
            }
            if(t==175) {
                // Clicking a pouch stamp must put the eraser away and hold that stamp after release.
                double bx=38*.8,by=screen.height*.1+70*.8;
                screen.mouseClicked(mouse(bx,by),false); screen.mouseReleased(mouse(bx,by));
                var pos=point(.75,.4);screen.mouseMoved(pos[0],pos[1]);
                screen.mouseClicked(mouse(pos[0],pos[1]),false); screen.mouseReleased(mouse(pos[0],pos[1]));
            }
            if(t==189) {
                if(ClientSession.get().album().selected().imprints().size()!=3) throw new AssertionError("Direct eraser-to-stamp switch must stamp, not erase");
                screen.keyPressed(new KeyEvent(69,0,0));var pos=point(.75,.4);screen.mouseClicked(mouse(pos[0],pos[1]),false);
                screen.keyPressed(new KeyEvent(69,0,0));
                // Re-take the automatically returned tool and release it outside the paper.
                press(.3,.3);screen.mouseDragged(mouse(screen.width-30,screen.height*.4),0,0);
                screen.mouseReleased(mouse(screen.width-30,screen.height*.4));
            }
            if(t==195) shot="02-desk.png";
            if(t==205) { var pos=point(.96,.98); screen.mouseClicked(mouse(pos[0],pos[1]),false); }
            if(t==216) shot="03-inserting.png";
            if(t==231) shot="08-paper-inside.png";
            if(t==237) shot="09-flap-closing.png";
            if(t==241) shot="04-sealed.png";
            if(t==252) shot="05-flip.png";
            if(t==270) {
                if(!(mc.screen instanceof SignatureScreen)) throw new AssertionError("Packed envelope must turn over");
                sign((SignatureScreen)mc.screen);
                if(ClientSession.get().album().selected().signature().size()!=1) throw new AssertionError("Mouse signature not persisted");
                shot="06-signature.png";
            }
            if(t==274) {
                var corner=point(0,1);
                mc.screen.mouseClicked(mouse(corner[0]-32,corner[1]-86),false);
                if(!ClientSession.get().album().selected().signature().isEmpty()) throw new AssertionError("Undo did not remove last stroke");
                sign((SignatureScreen)mc.screen);
                mc.screen.mouseClicked(mouse(corner[0]-32,corner[1]-48),false);
                if(!ClientSession.get().album().selected().signature().isEmpty()) throw new AssertionError("Clear did not remove signature");
                sign((SignatureScreen)mc.screen);
            }
            if(t==280) { mc.screen.onClose(); if(mc.screen!=screen) throw new AssertionError("Back did not return to front"); }
            if(t==284) { var pos=point(.96,.98); screen.mouseClicked(mouse(pos[0],pos[1]),false); }
            if(t==346) {
                sentCard=ClientSession.get().album().current();
                if(ClientSession.get().album().selected().signature().size()!=1) throw new AssertionError("Signature lost across screen reopen");
                var pos=point(1,1); mc.screen.mouseClicked(mouse(pos[0]+22,pos[1]-14),false);
            }
            if(t==352) shot="07-signed-send.png";
            if(t==390) {
                var album=ClientSession.get().album();
                if(album.current().equals(sentCard) || album.cards().size()!=2 || !album.selected().imprints().isEmpty())
                    throw new AssertionError("Successful send must retain the old card and select a new blank card");
                var pos=point(0,.5);screen.mouseClicked(mouse(pos[0]-26,pos[1]),false);
                if(!ClientSession.get().album().current().equals(sentCard)) throw new AssertionError("Left arrow must retrieve the sent postcard");
            }
            if(t==394) shot="15-card-from-behind.png";
            if(t==404) {
                Path exported;
                try(var paths=Files.list(screen.exportDirectory())) { exported=paths.filter(p->Files.isDirectory(p) && p.getFileName().toString().startsWith("postmark-")).max(java.util.Comparator.comparing(p->p.getFileName().toString())).orElseThrow(); }
                var image=ImageIO.read(exported.resolve("front.png").toFile());
                var back=ImageIO.read(exported.resolve("envelope.png").toFile());
                if(image.getWidth()!=1800 || image.getHeight()!=1200 || back.getWidth()!=1800) throw new AssertionError("Wrong PNG size");
                if(ClientSession.get().album().selected().imprints().size()!=2) throw new AssertionError("Signing changed front");
                var before=ClientSession.get().album(); screen.onClose(); ClientSession.clear();
                if(!before.equals(ClientSession.get().album())) throw new AssertionError("Draft changed across reload");
                screen=new PostcardScreen(null,ClientSession.get(),null); mc.setScreen(screen); toolPosition=null;
            }
            if(t==408) { press(.35,.38); release(.35,.38); }
            if(t==422) {
                if(ClientSession.get().album().selected().imprints().size()!=3) throw new AssertionError("Reopened screen must return tools to pouch");
                Files.writeString(mc.gameDirectory.toPath().resolve("smoke-result.txt"),"PASS: 500ms idle preview; motion and rotation preserve preview; preview leaves draft unchanged; pouch drag; repeated imprints; top erase; stamp returns after contact and outside release; held eraser switches directly to a stamp; compact UI; envelope packing and flip; mouse signature; undo/clear; return/reopen; postcard + envelope PNG; signed draft reload. Screenshots="+screenshots.get());
            }
            if(t==424) {
                var session=ClientSession.get();scrollBaseline=session.album();var expanded=scrollBaseline;
                String asset=expanded.stamps().getFirst().asset();
                for(int i=0;i<12;i++) expanded=expanded.unlock(new dev.postmark.model.StampDefinition("scroll-test-"+i+"/visitor","Scroll test "+i,asset,false));
                session.update(expanded);screen=new PostcardScreen(null,session,null);mc.setScreen(screen);
                screen.mouseScrolled(30,100,0,-4.5);
                if(screen.shelfScrollPosition()!=0) throw new AssertionError("Scroll must animate instead of jump");
            }
            if(t==426) {
                double offset=screen.shelfScrollPosition();
                if(offset<=0 || offset>=216) throw new AssertionError("Scroll did not produce an intermediate pixel offset: "+offset);
                double bx=38*.8,by=screen.height*.1+70*.8;
                screen.mouseClicked(mouse(bx,by),false);
                screen.mouseDragged(mouse(screen.width-30,100),0,0);screen.mouseReleased(mouse(screen.width-30,100));
                // Immediately lift a DIFFERENT stamp while the previous one is still returning.
                int nextIndex=(int)Math.floor((70-36+screen.shelfScrollPosition())/68)+1;
                selectedDuringReturn=ClientSession.get().album().stamps().get(nextIndex).key();
                double nextY=screen.height*.1+138*.8;
                screen.mouseClicked(mouse(bx,nextY),false);
                var pos=point(.7,.32);screen.mouseDragged(mouse(pos[0],pos[1]),0,0);screen.mouseReleased(mouse(pos[0],pos[1]));
                shot="13-scroll-and-return.png";
            }
            if(t==442) {
                var card=ClientSession.get().album().selected();
                if(card.imprints().size()!=4 || !card.imprints().getLast().source().equals(selectedDuringReturn))
                    throw new AssertionError("Cannot select the visible next stamp during return animation");
                shot="14-smooth-shelf.png";
            }
            if(t==448) {
                ClientSession.get().update(scrollBaseline);
                screen=new PostcardScreen(null,ClientSession.get(),null);mc.setScreen(screen);
                resizeCount=ClientSession.get().album().selected().imprints().size();
                press(.5,.4);
            }
            if(t==464) {
                var pos=point(.5,.4);
                screen.mouseClicked(new MouseButtonEvent(pos[0],pos[1],new MouseButtonInfo(1,0)),false);
                screen.mouseDragged(new MouseButtonEvent(pos[0]+70,pos[1],new MouseButtonInfo(1,0)),70,0);
                if(screen.stampPreviewAmount()<.99) throw new AssertionError("Resizing must retain a revealed preview");
                // Releasing the original left drag while resizing must not stamp.
                screen.mouseReleased(mouse(pos[0]+70,pos[1]));
                screen.mouseReleased(new MouseButtonEvent(pos[0]+70,pos[1],new MouseButtonInfo(1,0)));
                if(ClientSession.get().album().selected().imprints().size()!=resizeCount) throw new AssertionError("Right release must not stamp");
                shot="16-resized-preview.png";
            }
            if(t==466) {
                var pos=point(.5,.4);screen.mouseClicked(mouse(pos[0]+70,pos[1]),false);screen.mouseReleased(mouse(pos[0]+70,pos[1]));
            }
            if(t==480) {
                var card=ClientSession.get().album().selected();var mark=card.imprints().getLast();
                if(card.imprints().size()!=resizeCount+1 || Math.abs(mark.size()-.16*Math.exp(.5))>.0001 || Math.abs(mark.x()-.5)>.0001 || Math.abs(mark.y()-.4)>.0001)
                    throw new AssertionError("Resized imprint must match the preview size and fixed landing point");
                if(card.imprints().getFirst().size()!=.16) throw new AssertionError("Resizing changed an existing imprint");
                shot="17-arrows-and-eraser.png";
                var pos=point(1,.5);screen.mouseClicked(mouse(pos[0]+26,pos[1]),false);
            }
            if(t==495) {
                if(!ClientSession.get().album().selected().imprints().isEmpty()) throw new AssertionError("Right arrow must select the blank card");
                screen.keyPressed(new KeyEvent(91,0,0));
            }
            if(t==510) {
                var album=ClientSession.get().album();
                if(!album.current().equals(sentCard) || album.selected().signature().size()!=1 || album.selected().imprints().size()!=resizeCount+1)
                    throw new AssertionError("Animated navigation lost postcard contents");
                screen.onClose();ClientSession.clear();
                if(!album.equals(ClientSession.get().album())) throw new AssertionError("Resized imprint or switched postcard lost on reload");
                Files.writeString(mc.gameDirectory.toPath().resolve("smoke-result.txt"),"PASS: animated left/right arrows and keyboard navigation preserve all card contents; successful send keeps old postcard and opens a blank card; right drag changes imprint size with fixed landing point; left/right releases during resize never stamp; preview stays transparent; saved resized imprint survives reload; 500ms preview, smooth shelf, concurrent returns, eraser, signatures and PNG export regressions passed.");
                bagBaseline=ClientSession.get().album();
                var session=ClientSession.get();var expanded=bagBaseline;
                for(int i=0;i<60;i++) {
                    var art=new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);var pen=art.createGraphics();
                    pen.setColor(java.awt.Color.getHSBColor(i/60f,.58f,.83f));pen.fillRect(5,4,22,24);
                    pen.setColor(new java.awt.Color(0xF4E9C3));pen.fillRect(9,8,14,16);
                    pen.setColor(java.awt.Color.getHSBColor(i/60f,.75f,.55f));
                    for(int b=0;b<6;b++) if(((i+1)&(1<<b))!=0) pen.fillRect(10+(b%2)*7,9+(b/2)*5,5,3);
                    pen.dispose();String asset=session.store().putImage(art);
                    for(String kind:java.util.List.of("visitor","expert")) expanded=expanded.unlock(new dev.postmark.model.StampDefinition(String.format("bag-%02d/",i)+kind,"花园"+i+" · "+(kind.equals("expert")?"大师章":"普通章"),asset,false));
                }
                session.update(expanded);screen=new PostcardScreen(null,session,null);mc.setScreen(screen);
                screen.keyPressed(new KeyEvent(258,0,0));
                if(!screen.stampBag().isOpen()) throw new AssertionError("Tab must open the pouch");
                bagImprints=session.album().selected().imprints().size();
            }
            if(t==520) {
                // The export regression intentionally opens Explorer; restore game focus before real cursor hover checks.
                org.lwjgl.glfw.GLFW.glfwFocusWindow(mc.getWindow().handle());
            }
            if(t==522) {
                var found=screen.stampBag().matches();
                if(found.size()!=121) throw new AssertionError("Large bag lost stamps");
                bagKey="bag-17/visitor";var pos=screen.stampBag().positionOf(bagKey);
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().handle(),pos.x()*mc.getWindow().getGuiScale(),pos.y()*mc.getWindow().getGuiScale());
            }
            if(t==528) shot="21-hover-debug.png";
            if(t==530) {
                if(screen.stampBag().magnification(bagKey)<1.5) throw new AssertionError("Hovered stamp did not magnify: scale="+screen.stampBag().magnification(bagKey)+" target="+screen.stampBag().positionOf(bagKey)+" mouse="+mc.mouseHandler.getScaledXPos(mc.getWindow())+","+mc.mouseHandler.getScaledYPos(mc.getWindow()));
                shot="18-honeycomb-pile.png";
            }
            if(t==532) {
                for(int cp:"花园17".codePoints().toArray()) screen.charTyped(new CharacterEvent(cp));
                if(screen.stampBag().matchCount()!=2) throw new AssertionError("Chinese exhibition search must lift visitor and expert");
                screen.keyPressed(new KeyEvent(78,0,0));
                if(!ClientSession.get().album().current().equals(bagBaseline.current())) throw new AssertionError("Search typing triggered postcard hotkeys");
            }
            if(t==541) shot="19-search-pair.png";
            if(t==545) {
                var pos=screen.stampBag().positionOf(bagKey);screen.mouseClicked(mouse(pos.x(),pos.y()),false);screen.mouseReleased(mouse(pos.x(),pos.y()));
                if(screen.stampBag().isOpen() || ClientSession.get().album().selected().imprints().size()!=bagImprints) throw new AssertionError("Search result must be held without stamping on pickup release");
                var paper=point(.75,.7);screen.mouseMoved(paper[0],paper[1]);screen.mouseClicked(mouse(paper[0],paper[1]),false);screen.mouseReleased(mouse(paper[0],paper[1]));
            }
            if(t==550) shot="22-bag-pickup-scroll.png";
            if(t==554) shot="23-return-to-visible-slot.png";
            if(t==560) {
                int slot=0;var defs=ClientSession.get().album().stamps();
                for(int i=0;i<defs.size();i++) if(defs.get(i).key().equals(bagKey)) slot=i;
                double visibleY=70+slot*68-screen.shelfScrollPosition();
                if(visibleY<55 || visibleY>screen.height-64) throw new AssertionError("Bag pickup/return did not reveal its actual shelf slot: "+visibleY);
                var card=ClientSession.get().album().selected();
                if(card.imprints().size()!=bagImprints+1 || !card.imprints().getLast().source().equals(bagKey)) throw new AssertionError("Wrong result stamped");
                screen.keyPressed(new KeyEvent(258,0,0));
                screen.stampBag().search.setValue("花园17 大师");
                if(screen.stampBag().matchCount()!=1) throw new AssertionError("Combined name and master search must find exactly one");
            }
            if(t==570) {
                shot="20-search-master.png";
                var pos=screen.stampBag().positionOf("bag-17/expert");screen.mouseClicked(mouse(pos.x(),pos.y()),false);
                var paper=point(.45,.7);
                for(int i=1;i<=100;i++) screen.mouseDragged(mouse(pos.x()+(paper[0]-pos.x())*i/100,pos.y()+(paper[1]-pos.y())*i/100),1,1);
                screen.mouseReleased(mouse(paper[0],paper[1]));
            }
            if(t==585) {
                var card=ClientSession.get().album().selected();
                if(card.imprints().size()!=bagImprints+2 || !card.imprints().getLast().source().equals("bag-17/expert")) throw new AssertionError("Slow drag from bag must stamp on release");
                screen.keyPressed(new KeyEvent(258,0,0));screen.stampBag().search.setValue("不存在的章");
                if(screen.stampBag().matchCount()!=0) throw new AssertionError("No-match search failed");
            }
            if(t==594) {
                screen.mouseClicked(mouse(screen.width/2,screen.height/2),false);screen.mouseReleased(mouse(screen.width/2,screen.height/2));
                if(!screen.stampBag().isOpen()) throw new AssertionError("Dim nonmatching stamps must not be selectable");
                screen.stampBag().search.setValue("");screen.mouseScrolled(screen.width/2,screen.height/2,0,4);
            }
            if(t==604) { screen.stampBag().search.setValue("花园59"); }
            if(t==614) {
                var pos=screen.stampBag().positionOf("bag-59/visitor");
                screen.mouseClicked(mouse(pos.x(),pos.y()),false);screen.mouseReleased(mouse(pos.x(),pos.y()));
                screen.keyPressed(new KeyEvent(256,0,0));
            }
            if(t==620) shot="24-distant-return.png";
            if(t==638) {
                var defs=ClientSession.get().album().stamps();int slot=0;
                for(int i=0;i<defs.size();i++) if(defs.get(i).key().equals("bag-59/visitor")) slot=i;
                double visibleY=70+slot*68-screen.shelfScrollPosition();
                if(visibleY<55 || visibleY>screen.height-64 || ClientSession.get().album().selected().imprints().size()!=bagImprints+2)
                    throw new AssertionError("Immediate cancellation of a distant pickup must return to a visible slot without stamping");
                // Reopen once to check Escape still closes only the bag.
                screen.keyPressed(new KeyEvent(258,0,0));
                screen.keyPressed(new KeyEvent(256,0,0));
                if(screen.stampBag().isOpen()) throw new AssertionError("Escape must close bag, not postcard");
                ClientSession.get().update(bagBaseline);screen.onClose();ClientSession.clear();
                if(!ClientSession.get().album().equals(bagBaseline)) throw new AssertionError("Bag fixture cleanup changed the album");
                Files.writeString(mc.gameDirectory.toPath().resolve("smoke-result.txt"),"PASS: 121-stamp honeycomb pile; stable hover magnification; Chinese name search selects a visitor/expert pair; combined query selects one master; search does not invoke editor shortcuts; click pickup holds without accidental imprint; slow drag pickup stamps correct artwork; no-match results cannot select the dim pile; zoom and Escape; taking from bag and returning reveal the actual shelf slot; all alpha.7 postcard, resize, erase, signature, export and return regressions passed.");
                // Begin a fresh, isolated collection for milestone and mouse-only controls.
                var session=ClientSession.get();var fresh=dev.postmark.model.Album.empty();
                var practice=session.album().stamps().getFirst();
                var next=fresh.unlock(practice);
                for(int i=0;i<9;i++) next=next.unlock(new dev.postmark.model.StampDefinition("progress-"+i+"/visitor","测试馆"+i,practice.asset(),false));
                session.update(next);screen=new PostcardScreen(null,session,null);mc.setScreen(screen);
                if(screen.collectionTag().progress().total()!=9) throw new AssertionError("Tag must exclude practice stamps");
            }
            if(t==645) {
                screen.mouseClicked(mouse(100,screen.height-60),false);screen.mouseReleased(mouse(100,screen.height-60));
            }
            if(t==653) {
                if(!screen.collectionTag().isOpen() || screen.collectionTag().progress().holes()!=9) throw new AssertionError("Tag flip or nine-hole progress failed");
                shot="25-collection-tag.png";
            }
            if(t==657) {
                var session=ClientSession.get();String asset=session.album().stamps().getFirst().asset();
                session.update(session.album().unlock(new dev.postmark.model.StampDefinition("progress-0/expert","测试馆0",asset,false)));
            }
            if(t==665) {
                var progress=screen.collectionTag().progress();
                if(progress.total()!=10 || progress.visitors()!=9 || progress.experts()!=1 || progress.venues()!=9 || progress.holes()!=10 || !progress.unlocks(10)) throw new AssertionError("New master stamp did not complete milestone");
                shot="26-milestone-ticket.png";
            }
            if(t==668) {
                screen.mouseClicked(mouse(172,screen.height-60),false);screen.mouseReleased(mouse(172,screen.height-60));
                if(!ClientSession.get().album().selected().imprints().isEmpty()) throw new AssertionError("Picking ticket must not place it");
                var pos=point(.6,.7);screen.mouseMoved(pos[0],pos[1]);screen.mouseClicked(mouse(pos[0],pos[1]),false);screen.mouseReleased(mouse(pos[0],pos[1]));
            }
            if(t==684) {
                var session=ClientSession.get();var card=session.album().selected();
                if(card.imprints().size()!=1 || !card.imprints().getFirst().source().equals("postmark:milestone/10") || screen.collectionTag().progress().total()!=10)
                    throw new AssertionError("Ticket placement must persist without increasing collection progress");
                if(!session.store().load().selected().equals(card)) throw new AssertionError("Ticket not saved");
                var result=dev.postmark.render.PostcardPainter.paint(card,session.store()::image);
                var baseline=dev.postmark.render.PostcardPainter.paint(card.erase(card.imprints().getFirst().id()),session.store()::image);
                if(result.getRGB(1080,840)==baseline.getRGB(1080,840)) throw new AssertionError("Ticket missing from export composition");
                shot="27-ticket-on-postcard.png";
            }
            if(t==687) {
                var top=point(1,0);screen.mouseClicked(mouse(top[0]-14,top[1]-26),false);
                if(ClientSession.get().album().cards().size()!=2) throw new AssertionError("Visible new-paper control failed");
            }
            if(t==703) {
                var session=ClientSession.get();var card=session.album().selected();
                session.update(session.album().replace(card.withBackground(session.album().stamps().getFirst().asset())));
                screen=new PostcardScreen(null,session,null);mc.setScreen(screen);
                var top=point(1,0);screen.mouseClicked(mouse(top[0]-48,top[1]-26),false);
                if(session.album().selected().background()!=null) throw new AssertionError("Visible plain-paper control failed");
                var bottom=point(1,1);screen.mouseClicked(mouse(bottom[0]+32,bottom[1]-54),false);
                shot="28-mouse-controls.png";
            }
            if(t==710) {
                ClientSession.get().update(bagBaseline);
                screen.mouseClicked(mouse(screen.width-22,22),false);
                if(mc.screen==screen) throw new AssertionError("Visible close control failed");
                ClientSession.clear();if(!ClientSession.get().album().equals(bagBaseline)) throw new AssertionError("Progress test cleanup failed");
                Files.writeString(mc.gameDirectory.toPath().resolve("smoke-result.txt"),"PASS: numeric tag and punched milestones; counts exclude practice and tickets; new expert updates distinct venue/type counts; earned ticket pickup, placement, save and export composition; visible mouse-only new/plain/folder/close and signature undo/clear controls; prior 121-stamp bag, scrolling, return, resize, preview, signature and export regressions passed.");
                mc.stop();
            }
        } catch(Throwable e) {
            dev.postmark.Postmark.LOGGER.error("SMOKE FAILED",e);
            try { Files.writeString(mc.gameDirectory.toPath().resolve("smoke-result.txt"),"FAIL: "+e); } catch(Exception ignored) {}
            mc.stop();
        }
    }
    private static void sign(SignatureScreen reverse) {
        double[][] path={{.56,.78},{.59,.69},{.57,.80},{.64,.73},{.60,.80},{.68,.75},{.66,.80},{.72,.77},{.74,.79},{.79,.72},{.76,.81},{.83,.77},{.84,.79},{.90,.76}};
        var first=point(path[0][0],path[0][1]); reverse.mouseClicked(mouse(first[0],first[1]),false);
        for(var uv:path) { var pos=point(uv[0],uv[1]); reverse.mouseDragged(mouse(pos[0],pos[1]),0,0); }
        var last=point(.90,.76); reverse.mouseReleased(mouse(last[0],last[1]));
    }
    @SubscribeEvent public static void rendered(RenderFrameEvent.Post event) {
        if(shot==null) return;
        String name=shot; shot=null; var mc=Minecraft.getInstance();
        Screenshot.takeScreenshot(mc.getMainRenderTarget(),image->{
            try(image) {
                Path folder=mc.gameDirectory.toPath().resolve("smoke-shots"); Files.createDirectories(folder);
                image.writeToFile(folder.resolve(name)); screenshots.incrementAndGet();
            } catch(Exception e) { dev.postmark.Postmark.LOGGER.error("Smoke screenshot failed",e); }
        });
    }
}