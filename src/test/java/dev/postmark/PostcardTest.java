package dev.postmark;

import dev.postmark.model.*;
import dev.postmark.render.PostcardPainter;
import dev.postmark.storage.AlbumStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class PostcardTest {
    @TempDir Path temporary;
    @Test void duplicateStampsAreIndependentAndCannotMove() {
        Postcard original=Postcard.blank();
        Postcard first=original.stamp("a","ink",.4,.5,.16,0);
        Postcard second=first.stamp("a","ink",.4,.5,.16,Math.PI/4);
        assertTrue(original.imprints().isEmpty());
        assertEquals(1,first.imprints().size());
        assertEquals(2,second.imprints().size());
        assertNotEquals(second.imprints().get(0).id(),second.imprints().get(1).id());
        assertEquals(second.imprints().getLast(),second.topAt(.4,.5));
        Postcard erased=second.erase(second.topAt(.4,.5).id());
        assertEquals(first.imprints(),erased.imprints());
        assertThrows(UnsupportedOperationException.class,()->second.imprints().clear());
    }
    @Test void hitTestingUsesRotationAndPaperAspectRatio() {
        Imprint mark=new Imprint(UUID.randomUUID(),"a","ink",.5,.5,.2,Math.PI/4);
        assertTrue(mark.contains(.63,.5));
        assertFalse(mark.contains(.61,.665));
        Imprint straight=new Imprint(UUID.randomUUID(),"a","ink",.5,.5,.2,0);
        assertTrue(straight.contains(.5,.64));
        assertFalse(straight.contains(.5,.66));
    }
    @Test void mapStampIsSquareAndCenteredOnPlayer() {
        var rect=MapPlacement.at(100,50,-100,-100,400,300,2,.035);
        assertEquals(.5,rect.x()+rect.width()/2,1e-6);
        assertEquals(.5,rect.y()+rect.height()/2,1e-6);
        assertEquals(rect.width()*2000,rect.height()*1000,1e-4);
        var edge=MapPlacement.at(-100,-100,-100,-100,400,300,2,.035);
        assertEquals(0,edge.x()+edge.width()/2,1e-6);
        assertTrue(edge.x()<0); // Clip the border instead of lying about the player's position.
        assertThrows(IllegalArgumentException.class,()->MapPlacement.at(999,0,0,0,400,300,2,.035));
        assertThrows(IllegalArgumentException.class,()->MapPlacement.at(0,0,0,0,0,300,2,.035));
    }
    @Test void albumSurvivesReloadAndKeepsImageSnapshots() throws Exception {
        var store=new AlbumStore(temporary,"server:a/player:1");
        String asset=store.putImage(PostcardPainter.practice(0));
        Album album=Album.empty();
        album=album.unlock(new StampDefinition("exhibit/one","One",asset,false));
        album=album.replace(album.selected().stamp("exhibit/one",asset,.2,.3,.16,.4));
        store.save(album);
        assertEquals(album,new AlbumStore(temporary,"server:a/player:1").load());
        assertEquals(asset,store.putImage(PostcardPainter.practice(0)));
        assertNotEquals(store.directory(),new AlbumStore(temporary,"server:b/player:1").directory());
        assertThrows(java.io.IOException.class,()->store.assetPath("../../outside.png"));
    }
    @Test void corruptAlbumIsNotSilentlyOverwritten() throws Exception {
        var store=new AlbumStore(temporary,"broken");
        var path=store.directory().resolve("album.json");
        Files.writeString(path,"{broken");
        assertThrows(java.io.IOException.class,store::load);
        assertEquals("{broken",Files.readString(path));
    }
    @Test void exportIsFullResolutionAndClipsEdgeStamps() throws Exception {
        var store=new AlbumStore(temporary,"export");
        String asset=store.putImage(PostcardPainter.practice(0));
        var card=Postcard.blank().stamp("practice",asset,0,.5,.16,0).stamp("practice",asset,.6,.5,.16,.2);
        var plain=PostcardPainter.paint(Postcard.blank(),store::image);
        var image=PostcardPainter.paint(card,store::image);
        assertEquals(1800,image.getWidth()); assertEquals(1200,image.getHeight());
        assertEquals(plain.getRGB(100,100),image.getRGB(100,100));
        int difference=0;
        for(int y=0;y<1200;y++) for(int x=0;x<1800;x++) {
            assertEquals(255,image.getRGB(x,y)>>>24);
            if(image.getRGB(x,y)!=plain.getRGB(x,y)) difference++;
        }
        assertTrue(difference>1000);
        Path png=temporary.resolve("postcard.png"); ImageIO.write(image,"PNG",png.toFile());
        assertEquals(image.getRGB(1080,600),ImageIO.read(png.toFile()).getRGB(1080,600));
    }
    @Test void invalidCoordinatesAndSchemaAreRejected() {
        assertThrows(IllegalArgumentException.class,()->Postcard.blank().stamp("a","b",Double.NaN,.5,.1,0));
        assertThrows(IllegalArgumentException.class,()->Postcard.blank().stamp("a","b",.5,.5,.1,Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,()->new Album(2,UUID.randomUUID(),List.of(Postcard.blank()),List.of()));
    }

    @Test void signaturePersistsWithoutChangingFrontAndExportsBothSides() throws Exception {
        var store=new AlbumStore(temporary,"signed");
        var album=Album.empty(); var original=album.selected();
        var stroke=new InkStroke(List.of(new InkPoint(.2,.3),new InkPoint(.6,.7)),0xff364f62,.0022);
        var signed=original.withSignature(List.of(stroke));
        store.save(album.replace(signed));
        assertEquals(signed,store.load().selected());
        assertTrue(original.signature().isEmpty());
        assertEquals(signed.signature(),signed.withBackground(null).stamp("x","asset",.4,.5,.1,0).signature());
        var plainFront=PostcardPainter.paint(original,store::image);
        var folder=dev.postmark.storage.PostcardExporter.export(store,signed);
        var front=ImageIO.read(folder.resolve("front.png").toFile());
        var back=ImageIO.read(folder.resolve("envelope.png").toFile());
        assertEquals(1800,back.getWidth()); assertEquals(1200,back.getHeight());
        assertArrayEquals(plainFront.getRGB(0,0,1800,1200,null,0,1800),front.getRGB(0,0,1800,1200,null,0,1800));
        assertEquals(0xff364f62,back.getRGB(720,600));
        assertThrows(UnsupportedOperationException.class,()->stroke.points().clear());
    }
    @Test void oldDraftWithoutSignatureMigratesAndFailedExportPublishesNothing() throws Exception {
        var store=new AlbumStore(temporary,"old"); var album=Album.empty();
        store.save(album);
        var json=store.directory().resolve("album.json");
        Files.writeString(json,Files.readString(json).replaceAll(",\\s*\"signature\"\\s*:\\s*\\[\\]",""));
        assertEquals(album,store.load());
        var invalid=album.selected().withBackground("missing.png");
        assertThrows(java.io.IOException.class,()->dev.postmark.storage.PostcardExporter.export(store,invalid));
        try(var paths=Files.list(store.directory().resolve("exports"))) { assertEquals(0,paths.count()); }
        assertEquals(album,store.load());
    }
    @Test void signatureRejectsInvalidOrExcessiveInput() {
        assertThrows(IllegalArgumentException.class,()->new InkPoint(Double.NaN,0));
        assertThrows(IllegalArgumentException.class,()->new InkPoint(1.1,0));
        assertThrows(IllegalArgumentException.class,()->new InkStroke(List.of(),0xff000000,.002));
        var stroke=new InkStroke(List.of(new InkPoint(.5,.5)),0xff000000,.002);
        assertThrows(IllegalArgumentException.class,()->Postcard.blank().withSignature(java.util.Collections.nCopies(257,stroke)));
    }
    @Test void mapSizeTracksVisiblePlayerIconAndZoom() {
        double ratio=MapPlacement.playerSizedRatio(4000,19.0/32);
        assertEquals(30*19.0/32*1.15,ratio*4000,1e-8);
        assertTrue(ratio<.035/5);
        assertEquals(ratio/2,MapPlacement.playerSizedRatio(8000,19.0/32),1e-9);
        assertThrows(IllegalArgumentException.class,()->MapPlacement.playerSizedRatio(0,.5));
    }
    @Test void expertStyleUsesExactStampIdAndWorksWithExistingSavedDefinitions() throws Exception {
        var store=new AlbumStore(temporary,"expert-style");
        String asset=store.putImage(PostcardPainter.practice(0));
        var expert=new StampDefinition("exhibition/expert","same artwork",asset,false);
        var visitor=new StampDefinition("exhibition/visitor","expert",asset,false);
        var album=Album.empty().unlock(expert).unlock(visitor);store.save(album);
        var restored=store.load();
        assertTrue(restored.stamps().getFirst().expert());
        assertFalse(restored.stamps().getLast().expert());
        assertEquals(expert.asset(),visitor.asset());
        assertFalse(new StampDefinition("exhibition/expert_extra","expert",asset,false).expert());
        assertFalse(new StampDefinition("practice/expert","expert",asset,true).expert());
        assertEquals(album,restored); // Styling requires no album migration or replacement artwork.
    }
    @Test void previewLatchesAfterHalfSecondUntilTheNextPickup() {
        var timer=new dev.postmark.client.StampPreviewTimer();assertEquals(0,timer.amount(9000));
        timer.reset(10,20,0,100);timer.update(11,20,0,400);
        assertEquals(0,timer.amount(899));assertEquals(.5f,timer.amount(990),.001f);assertEquals(1,timer.amount(1080));
        timer.update(30,50,.3,1100);assertEquals(1,timer.amount(1100));
        timer.reset(30,50,.3,1200);assertEquals(0,timer.amount(1200));
        timer.update(30,50,.3,1600);assertEquals(1,timer.amount(1880));
    }
    @Test void shelfScrollIsContinuousClampedAndFrameRateIndependent() {
        var one=new dev.postmark.client.SmoothShelfScroll();var many=new dev.postmark.client.SmoothShelfScroll();
        one.bounds(600);many.bounds(600);one.advance(0);many.advance(0);one.scroll(-2.5);many.scroll(-2.5);
        assertEquals(0,one.position());one.advance(100);
        for(int i=10;i<=100;i+=10) many.advance(i);
        assertTrue(one.position()>0 && one.position()<120);assertEquals(one.position(),many.position(),1e-9);
        one.scroll(100);one.advance(200);assertTrue(one.position()>=0);one.advance(2000);assertEquals(0,one.position());
        one.scroll(-100);one.advance(4000);assertEquals(600,one.position());one.bounds(0);assertEquals(0,one.position());
    }
    @Test void toolCompositesLayersBeforeApplyingTransparency() {
        var art=PostcardPainter.practice(0);
        var normal=dev.postmark.render.StampToolPainter.paint(new StampDefinition("e/visitor","visitor","x",false),art);
        var expert=dev.postmark.render.StampToolPainter.paint(new StampDefinition("e/expert","expert","x",false),art);
        assertEquals(80,normal.getWidth());assertEquals(104,normal.getHeight());
        assertEquals(0,normal.getRGB(0,0)>>>24);assertEquals(255,normal.getRGB(12,40)>>>24);
        assertNotEquals(normal.getRGB(40,10),expert.getRGB(40,10));
        assertEquals(normal.getRGB(40,64),expert.getRGB(40,64));
    }
    @Test void bagSearchFindsAnExhibitionPairOrOneKindAndPreservesOrder() {
        var visitor=new StampDefinition("garden/visitor","花园17 · 普通章","a",false);
        var expert=new StampDefinition("garden/expert","花园17 · 大师章","a",false);
        var other=new StampDefinition("zoo/visitor","动物园 · 普通章","b",false);
        var input=List.of(expert,other,visitor);
        var ordered=dev.postmark.client.StampBagIndex.ordered(input);
        assertEquals(List.of(visitor,expert,other),ordered);
        assertEquals(2,ordered.stream().filter(v->dev.postmark.client.StampBagIndex.matches(v,"花园17")).count());
        assertTrue(dev.postmark.client.StampBagIndex.matches(expert,"花园17  ＥＸＰＥＲＴ"));
        assertFalse(dev.postmark.client.StampBagIndex.matches(visitor,"花园17 大师"));
        assertFalse(dev.postmark.client.StampBagIndex.matches(visitor,"不存在"));
        Album album=Album.empty().unlock(visitor).unlock(other);
        var renamed=new StampDefinition(visitor.key(),"新展区名 · 普通章",visitor.asset(),false);
        var next=album.unlock(renamed);
        assertEquals(List.of(renamed,other),next.stamps());
        assertEquals(List.of(visitor,other),album.stamps());
    }
    @Test void honeycombCentersRemainUniqueAndEquidistantAcrossRows() {
        var points=new java.util.HashSet<dev.postmark.client.StampBagIndex.Point>();
        for(int i=0;i<4096;i++) assertTrue(points.add(dev.postmark.client.StampBagIndex.cell(i,16)));
        var a=dev.postmark.client.StampBagIndex.cell(0,16);
        var b=dev.postmark.client.StampBagIndex.cell(1,16);
        var c=dev.postmark.client.StampBagIndex.cell(16,16);
        assertEquals(42,Math.hypot(b.x()-a.x(),b.y()-a.y()),.01);
        assertEquals(42,Math.hypot(c.x()-a.x(),c.y()-a.y()),.01);
    }
    @Test void missingKindsAndSharedArtworkNeverInventOrMergeStamps() {
        var visitor=new StampDefinition("hall-a/visitor","甲馆 · 普通章","shared-image",false);
        var expert=new StampDefinition("hall-b/expert","乙馆 · 大师章","shared-image",false);
        var other=new StampDefinition("hall-c/visitor","丙馆 · 普通章","shared-image",false);
        var album=Album.empty().unlock(visitor).unlock(expert).unlock(other);
        assertEquals(3,album.stamps().size());
        assertEquals(List.of(visitor),album.stamps().stream().filter(v->dev.postmark.client.StampBagIndex.matches(v,"甲馆")).toList());
        assertEquals(List.of(expert),album.stamps().stream().filter(v->dev.postmark.client.StampBagIndex.matches(v,"乙馆")).toList());
        assertTrue(album.stamps().stream().noneMatch(v->dev.postmark.client.StampBagIndex.matches(v,"无章馆")));
        var card=album.selected().stamp(visitor.key(),visitor.asset(),.2,.5,.16,0).stamp(other.key(),other.asset(),.7,.5,.16,0);
        assertNotEquals(card.imprints().getFirst().source(),card.imprints().getLast().source());
        assertEquals(card.imprints().getFirst().asset(),card.imprints().getLast().asset());
    }
    @Test void revealingDistantShelfSlotsAnimatesAndRespectsBothEnds() {
        var scroll=new dev.postmark.client.SmoothShelfScroll();scroll.bounds(121*68-372);scroll.advance(0);
        scroll.reveal(70+35*68,55,386);
        assertEquals(0,scroll.position());
        assertTrue(scroll.settlingMillis()>240);
        scroll.advance(75);assertTrue(scroll.position()>0 && scroll.position()<2000);
        scroll.advance(1500);assertTrue(70+35*68-scroll.position()>=55 && 70+35*68-scroll.position()<=386);
        double settled=scroll.position();scroll.reveal(70+35*68,55,386);scroll.advance(2000);assertEquals(settled,scroll.position());
        scroll.reveal(70+120*68,55,386);scroll.advance(4000);
        assertEquals(scroll.maximum(),scroll.position());
        assertTrue(70+120*68-scroll.position()<=386);
        scroll.reveal(70,55,386);scroll.advance(6000);assertEquals(0,scroll.position());
    }
}