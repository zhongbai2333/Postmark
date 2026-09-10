package dev.postmark;

import dev.postmark.model.*;
import dev.postmark.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StampVariantsTest {
    @TempDir Path directory;
    private final UUID venue=UUID.randomUUID();
    private StampDefinition stamp(String item,String asset) {return new StampDefinition(StampIdentity.key(venue,"visitor",item),"普通章",asset,false);}
    @Test void twoVisitorsStaySeparateThroughScanningCollectingSavingAndReset() throws Exception {
        var guideVenue=new GuideVenue(venue,"双普通章馆","",0,0,0,0,0);
        var journal=TravelJournal.empty().surveyed(Set.of(venue),List.of(
                new TravelJournal.Discovery(venue,"visitor","minecraft:paper"),new TravelJournal.Discovery(venue,"visitor","minecraft:diamond"),
                new TravelJournal.Discovery(venue,"visitor","minecraft:paper"))).areaSurveyed(List.of(guideVenue));
        var paper=stamp("minecraft:paper","a".repeat(64)+".png");var diamond=stamp("minecraft:diamond","b".repeat(64)+".png");
        var album=Album.empty().unlockCaptured(paper);var catalog=StampCatalog.bundled();
        var partial=journal.stamps(venue,album.stamps());assertEquals(2,partial.size());
        assertEquals(1,partial.stream().filter(TravelJournal.KnownStamp::owned).count());assertFalse(catalog.complete(venue,partial,true));
        album=album.unlockCaptured(diamond).reconcileStamps(Set.of(venue.toString()),Set.of(venue+"/visitor"));
        assertEquals(List.of(paper,diamond),album.stamps());assertTrue(catalog.complete(venue,journal.stamps(venue,album.stamps()),true));
        assertEquals(2,CollectionProgress.from(album.stamps()).visitors());assertEquals(1,CollectionProgress.from(album.stamps()).venues());
        var store=new AlbumStore(directory,"variants");store.save(album);assertEquals(album,store.load());
        var journalStore=new TravelJournalStore(store.directory());journalStore.save(journal);assertEquals(journal,journalStore.load());
        album=album.replace(album.selected().stamp(paper.key(),paper.asset(),.5,.5,.2,0));var cards=album.cards();
        album=album.reconcileStamps(Set.of(venue.toString()),Set.of());assertTrue(album.stamps().isEmpty());assertEquals(cards,album.cards());
        album=album.unlockCaptured(diamond);assertEquals(1,journal.stamps(venue,album.stamps()).stream().filter(TravelJournal.KnownStamp::owned).count());
        assertFalse(catalog.complete(venue,journal.stamps(venue,album.stamps()),true));
    }
    @Test void presetsReplaceCategoryGhostsButKeepEveryActualVariant() {
        var id=UUID.fromString("bf52f38c-3531-54fa-a83c-97eeafd51b54");var catalog=StampCatalog.bundled();
        var observed=List.of(new TravelJournal.KnownStamp("visitor","minecraft:paper","a",true),new TravelJournal.KnownStamp("visitor","minecraft:diamond",null,false),new TravelJournal.KnownStamp("expert","minecraft:emerald","b",true));
        assertEquals(3,catalog.display(id,observed,false).size());assertFalse(catalog.complete(id,observed));
        var all=observed.stream().map(s->new TravelJournal.KnownStamp(s.id(),s.item(),"a",true)).toList();assertTrue(catalog.complete(id,all));
    }
    @Test void legacyMigrationMatchesArtworkAndPreservesOldPostcardKeys() {
        var paper=stamp("minecraft:paper","a".repeat(64)+".png");var diamond=stamp("minecraft:diamond","b".repeat(64)+".png");
        var legacy=new StampDefinition(venue+"/visitor","old",paper.asset(),false);
        var album=Album.empty().unlock(legacy);album=album.replace(album.selected().stamp(legacy.key(),legacy.asset(),.5,.5,.2,0));
        var cards=album.cards();album=album.unlockCaptured(diamond);assertEquals(2,album.stamps().size());
        album=album.unlockCaptured(paper);assertEquals(List.of(diamond,paper),album.stamps());assertEquals(cards,album.cards());
        var unknown=TravelJournal.empty().surveyed(Set.of(venue),List.of(new TravelJournal.Discovery(venue,"visitor","minecraft:diamond")));
        assertFalse(unknown.stamps(venue,List.of(legacy)).stream().filter(s->"minecraft:diamond".equals(s.item())).findFirst().orElseThrow().owned());
    }
    @Test void variantTicketsSurviveSlotReplacementButCannotRestoreAfterClear() {
        var cache=new StampCaptureCache();var a=stamp("minecraft:paper","a");var b=stamp("minecraft:diamond","b");
        var first=cache.start(a.key(),"minecraft:paper");var second=cache.start(b.key(),"minecraft:diamond");
        cache.retainSlots(Set.of(venue+"/visitor"));assertTrue(cache.current(a.key(),first));assertTrue(cache.current(b.key(),second));
        cache.retainSlots(Set.of());assertFalse(cache.current(a.key(),first));assertFalse(cache.current(b.key(),second));
    }
    @Test void identityRoundTripsPathItemsAndKeepsExpertClassification() {
        String item="example:stamps/path.with-dots";String key=StampIdentity.key(venue,"expert",item);
        assertEquals("expert",StampIdentity.id(key));assertEquals(item,StampIdentity.item(key));assertEquals(venue+"/expert",StampIdentity.slot(key));
        assertTrue(new StampDefinition(key,"master","a",false).expert());
        assertNull(StampIdentity.item(venue+"/visitor"));assertEquals("visitor",StampIdentity.id(venue+"/visitor"));
    }
    @Test void exportKeepsHistoricalEarnedVariantDistinctFromCurrentServerSlot() {
        var data=StampCatalogExport.create("test",List.of(new StampCatalogExport.Venue(venue,"馆",true,true,true,List.of(
                new StampCatalogExport.Stamp("visitor","minecraft:paper","local_collected"),
                new StampCatalogExport.Stamp("visitor","minecraft:diamond","server_owned")))));
        assertEquals(2,data.ownedCount());assertEquals(2,data.venues().getFirst().stamps().size());assertFalse(data.completeCatalog());
    }
}
