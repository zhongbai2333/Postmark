package dev.postmark;

import dev.postmark.model.*;
import dev.postmark.storage.AlbumStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StampResetTest {
    @TempDir Path directory;
    private final String venue=UUID.randomUUID().toString(),other=UUID.randomUUID().toString();
    private StampDefinition stamp(String key) {return new StampDefinition(key,key,"a".repeat(64)+".png",key.startsWith("practice:"));}
    @Test void resetPreservesCardsPracticeAssetsAndUnrepresentedVenues() throws Exception {
        var visitor=stamp(venue+"/visitor");
        var album=Album.empty().unlock(stamp("practice:0")).unlock(visitor).unlock(stamp(venue+"/expert")).unlock(stamp(other+"/visitor"));
        album=album.replace(album.selected().stamp(visitor.key(),visitor.asset(),.5,.5,.2,.1));
        var cleared=album.reconcileStamps(Set.of(venue),Set.of());
        assertEquals(album.cards(),cleared.cards());assertEquals(album.current(),cleared.current());
        assertEquals(List.of(stamp("practice:0"),stamp(other+"/visitor")),cleared.stamps());
        var store=new AlbumStore(directory,"test");store.save(cleared);assertEquals(cleared,store.load());
        assertSame(album,album.reconcileStamps(Set.of(),Set.of()));
    }
    @Test void sequentialRecollectionUnlocksOnlyServerOwnedStampsAndUpdatesGuide() {
        var visitor=stamp(StampIdentity.key(UUID.fromString(venue),"visitor","minecraft:paper"));var expert=stamp(StampIdentity.key(UUID.fromString(venue),"expert","minecraft:paper"));
        var album=Album.empty().unlock(visitor).unlock(expert);
        var id=UUID.fromString(venue);var journal=TravelJournal.empty().surveyed(Set.of(id),List.of(
                new TravelJournal.Discovery(id,"visitor","minecraft:paper"),new TravelJournal.Discovery(id,"expert","minecraft:paper")));
        album=album.reconcileStamps(Set.of(venue),Set.of());
        assertTrue(journal.stamps(id,album.stamps()).stream().noneMatch(TravelJournal.KnownStamp::owned));
        assertTrue(journal.entry(id).searched());
        album=album.unlock(visitor);assertEquals(List.of(visitor),album.stamps());
        assertFalse(TravelJournal.regularComplete(journal.stamps(id,album.stamps())));
        album=album.unlock(expert);assertTrue(TravelJournal.regularComplete(journal.stamps(id,album.stamps())));
        assertEquals(List.of(expert),album.reconcileStamps(Set.of(venue),Set.of(StampIdentity.slot(expert.key()))).stamps());
    }
    @Test void lateCaptureCannotUndoClearOrOverwriteReacquiredSameArtwork() {
        var cache=new StampCaptureCache();String key=venue+"/visitor";
        var old=cache.start(key,"minecraft:paper");cache.retain(Set.of());
        assertFalse(cache.current(key,old));
        var fresh=cache.start(key,"minecraft:paper");assertFalse(cache.current(key,old));assertTrue(cache.current(key,fresh));
        cache.failed(key,old);assertTrue(cache.current(key,fresh));
        var changed=cache.start(key,"minecraft:diamond");assertFalse(cache.current(key,fresh));
        cache.retain(Set.of(key));assertTrue(cache.current(key,changed));
        cache.clear();assertFalse(cache.current(key,changed));
    }
}
