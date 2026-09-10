package dev.postmark;

import dev.postmark.model.GuideEntryPosition;
import dev.postmark.model.GuideViewport;
import dev.postmark.storage.GuideEntryPositionStore;
import dev.postmark.storage.GuideViewStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class GuidePreferencesTest {
    @TempDir Path directory;
    @Test void entryPositionPersistsAndStaysVisibleAtDifferentGuiScales() throws Exception {
        var store=new GuideEntryPositionStore(directory);assertNull(store.load());
        var position=GuideEntryPosition.at(600,340,720,450,54,40);store.save(position);
        assertEquals(position,store.load());
        for(int width:new int[]{320,480,720,1200}) {
            int height=width*5/8,x=position.x(width,54),y=position.y(height,40);
            assertTrue(x>=6&&x+54<=width-6);assertTrue(y>=6&&y+40<=height-6);
        }
        var corner=GuideEntryPosition.at(-999,9999,720,450,54,40);
        assertEquals(6,corner.x(720,54));assertEquals(404,corner.y(450,40));
    }
    @Test void invalidSavedPositionDoesNotOverwriteOriginal() throws Exception {
        Path file=directory.resolve("postmark/guide-entry-position.json");Files.createDirectories(file.getParent());Files.writeString(file,"broken");
        assertThrows(java.io.IOException.class,()->new GuideEntryPositionStore(directory).load());assertEquals("broken",Files.readString(file));
        assertThrows(IllegalArgumentException.class,()->new GuideEntryPosition(Double.NaN,0));
    }
    @Test void cameraPersistsAndRestoresZoomAndCenterAfterResize() throws Exception {
        var view=new GuideViewport();view.resize(640,360,1800,1300,true);view.readable();view.zoomAt(270,190,1.4);view.step(.1);view.pan(70,-30);
        var bookmark=view.bookmark();var store=new GuideViewStore(directory);store.save(bookmark);
        var reopened=new GuideViewport();reopened.resize(640,360,1800,1300,true);reopened.restore(store.load());
        assertEquals(bookmark.zoom(),reopened.zoom(),1e-9);assertEquals(bookmark,reopened.bookmark());
        reopened.resize(440,250,1800,1300,false);
        assertEquals(bookmark.centerX(),reopened.bookmark().centerX(),1e-9);assertEquals(bookmark.centerY(),reopened.bookmark().centerY(),1e-9);
        assertEquals(bookmark.zoom(),reopened.zoom(),1e-9);
        // Appending venues and resizing must keep a bounded, finite camera rather than forcing a fit.
        reopened.resize(440,250,2300,1700,false);assertEquals(bookmark.zoom(),reopened.zoom(),1e-9);
    }
    @Test void corruptCameraDoesNotAffectOtherSavedPreferences() throws Exception {
        var entryStore=new GuideEntryPositionStore(directory);entryStore.save(new GuideEntryPosition(.2,.3));
        Files.writeString(directory.resolve("guide-view.json"),"{\"zoom\":-1}");
        assertThrows(java.io.IOException.class,()->new GuideViewStore(directory).load());
        assertEquals(new GuideEntryPosition(.2,.3),entryStore.load());
    }
}
