package dev.postmark;

import dev.postmark.model.*;
import dev.postmark.render.*;
import dev.postmark.storage.AlbumStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VenueStickerTest {
    @TempDir Path folder;
    @Test void paperPreservesTheEntireVenueImageAndTransparentOutside() {
        var icon=new BufferedImage(80,80,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<80;y++)for(int x=0;x<80;x++)icon.setRGB(x,y,0xFF000000|(x<<16)|(y<<8)|33);
        for(int variant=0;variant<4;variant++) {
            var sticker=VenueStickerPainter.paint(icon,null,80,variant);assertEquals(256,sticker.getWidth());assertEquals(256,sticker.getHeight());
            for(int y=0;y<80;y++)for(int x=0;x<80;x++)assertEquals(icon.getRGB(x,y),sticker.getRGB((x+20)*2,(y+20)*2));
            assertEquals(0,sticker.getRGB(0,0));assertEquals(0,sticker.getRGB(255,255));
        }
    }
    @Test void stickerPersistsAndExportsWithoutBecomingACollectedStamp() throws Exception {
        var store=new AlbumStore(folder,"test");var sticker=VenueStickerPainter.paint(null,null,80,2);var asset=store.putImage(sticker);
        var album=Album.empty();var source="postmark:venue/"+UUID.randomUUID();
        var card=album.selected().stamp(source,asset,.5,.5,.34,.12);album=album.replace(card);store.save(album);
        assertTrue(store.load().stamps().isEmpty());assertEquals(source,store.load().selected().imprints().getFirst().source());
        assertNotEquals(PostcardPainter.paint(Postcard.blank(),store::image).getRGB(900,600),PostcardPainter.paint(card,store::image).getRGB(900,600));
        assertEquals(0,CollectionProgress.from(album.stamps()).total());
    }
}
