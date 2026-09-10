package dev.postmark;

import dev.postmark.model.*;
import dev.postmark.storage.TravelJournalStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyAreaTest {
    @TempDir Path directory;
    private final UUID id=UUID.randomUUID();
    private GuideVenue venue(int x) {return new GuideVenue(id,"馆","",x,20,-1,0,0);}
    private TravelJournal.KnownStamp stamp(String type,boolean owned) {return new TravelJournal.KnownStamp(type,"minecraft:paper",owned?"image.png":null,owned);}
    @Test void everyIntersectingChunkMustBeInspectedIncludingNegativeBoundaries() {
        var area=SurveyArea.around(venue(-1));var coords=new HashSet<String>();
        assertTrue(area.covered((x,z)->{coords.add(x+","+z);return true;}));
        assertEquals(81,coords.size());assertTrue(coords.contains("-5,-5"));assertTrue(coords.contains("3,3"));
        assertFalse(area.covered((x,z)->x!=-5 || z!=-5));
        assertFalse(area.covered((x,z)->x!=3 || z!=3));
    }
    @Test void legacySurveyIsNotAreaCoverageAndAreaEvidenceRoundTrips() throws Exception {
        var store=new TravelJournalStore(directory);
        Files.writeString(directory.resolve("travel-journal.json"),"{\"version\":1,\"entries\":[{\"venue\":\""+id+"\",\"searched\":true,\"stamps\":[]}]}");
        var old=store.load();assertFalse(old.areaSearched(venue(10)));
        var next=old.areaSurveyed(List.of(venue(10)));store.save(next);
        assertEquals(next,store.load());assertTrue(next.areaSearched(venue(10)));
        assertFalse(next.areaSearched(venue(11)));
        assertTrue(next.entry(id).stamps().isEmpty());
    }
    @Test void fullAreaResolvesSingleOrEmptyButNeverGrantsCollection() {
        var catalog=StampCatalog.bundled();var single=List.of(stamp("visitor",false));
        assertTrue(catalog.needsInspection(id,true,single,false));
        assertFalse(catalog.needsInspection(id,true,single,true));
        assertFalse(catalog.complete(id,single,true));
        assertTrue(catalog.complete(id,List.of(stamp("visitor",true)),true));
        assertFalse(catalog.needsInspection(id,true,List.of(),true));
        assertFalse(catalog.complete(id,List.of(),true));
    }
    @Test void positiveCatalogEvidenceSurvivesAnEmptyAreaScan() {
        var catalog=StampCatalog.bundled();var paired=UUID.fromString("bf52f38c-3531-54fa-a83c-97eeafd51b54");
        assertTrue(catalog.needsInspection(paired,true,List.of(stamp("visitor",true)),true));
        assertFalse(catalog.complete(paired,List.of(stamp("visitor",true)),true));
        assertEquals(2,catalog.merge(paired,List.of()).size());
    }
    @Test void laterExtraStampRevokesSingleCompletionAndPreservesCoverage() {
        var journal=TravelJournal.empty().areaSurveyed(List.of(venue(0)));
        journal=journal.surveyed(Set.of(id),List.of(new TravelJournal.Discovery(id,"special","minecraft:diamond")));
        assertTrue(journal.areaSearched(venue(0)));
        assertFalse(StampCatalog.bundled().complete(id,List.of(stamp("visitor",true),stamp("special",false)),true));
    }
}
