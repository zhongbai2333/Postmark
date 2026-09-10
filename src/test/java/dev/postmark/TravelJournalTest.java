package dev.postmark;

import dev.postmark.model.*;
import dev.postmark.storage.TravelJournalStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TravelJournalTest {
    @TempDir Path directory;
    private final UUID a=UUID.randomUUID(),b=UUID.randomUUID();
    private StampDefinition owned(UUID id,String type) {return new StampDefinition(StampIdentity.key(id,type,type.equals("special")?"minecraft:diamond":"minecraft:paper"),type,"a".repeat(64)+".png",false);}
    @Test void discoveryIsNotASurveyOrAnOwnershipGrant() {
        var journal=TravelJournal.empty().surveyed(Set.of(),List.of(new TravelJournal.Discovery(a,"visitor","minecraft:paper")));
        assertFalse(journal.entry(a).searched());assertFalse(journal.stamps(a,List.of()).getFirst().owned());
        assertFalse(TravelJournal.regularComplete(journal.stamps(a,List.of())));
    }
    @Test void zeroAndSingleStampSurveysDoNotInferCompletion() {
        var journal=TravelJournal.empty().surveyed(Set.of(a,b),List.of(new TravelJournal.Discovery(b,"visitor","minecraft:paper")));
        assertTrue(journal.entry(a).searched());assertTrue(journal.entry(a).stamps().isEmpty());
        assertFalse(TravelJournal.regularComplete(journal.stamps(a,List.of())));
        assertFalse(TravelJournal.regularComplete(journal.stamps(b,List.of(owned(b,"visitor")))));
    }
    @Test void repeatedCountersDeduplicateOnlyIdenticalArtwork() {
        var journal=TravelJournal.empty().surveyed(Set.of(a),List.of(new TravelJournal.Discovery(a,"visitor","minecraft:paper"),new TravelJournal.Discovery(a,"visitor","minecraft:grass_block"),new TravelJournal.Discovery(a,"visitor","minecraft:paper"),new TravelJournal.Discovery(b,"visitor","minecraft:paper")));
        assertEquals(2,journal.entry(a).stamps().size());assertEquals(Set.of("minecraft:paper","minecraft:grass_block"),journal.entry(a).stamps().stream().map(TravelJournal.FoundStamp::item).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1,journal.entry(b).stamps().size());assertFalse(journal.entry(b).searched());
    }
    @Test void extraUncollectedStampRevokesRegularCompletionWithoutLosingCorners() {
        var owned=List.of(owned(a,"visitor"),owned(a,"expert"));
        var journal=TravelJournal.empty();assertTrue(TravelJournal.regularComplete(journal.stamps(a,owned)));
        assertFalse(journal.entry(a).searched());
        journal=journal.surveyed(Set.of(a),List.of(new TravelJournal.Discovery(a,"special","minecraft:diamond")));
        assertEquals(3,journal.stamps(a,owned).size());assertFalse(TravelJournal.regularComplete(journal.stamps(a,owned)));
        var all=new ArrayList<>(owned);all.add(owned(a,"special"));assertTrue(TravelJournal.regularComplete(journal.stamps(a,all)));
    }
    @Test void journalRoundTripsAndSeparateDirectoriesStayIsolated() throws Exception {
        var store=new TravelJournalStore(directory.resolve("a"));var other=new TravelJournalStore(directory.resolve("b"));
        var journal=TravelJournal.empty().surveyed(Set.of(a),List.of(new TravelJournal.Discovery(a,"expert","minecraft:paper")));
        store.save(journal);assertEquals(journal,store.load());assertEquals(TravelJournal.empty(),other.load());
    }
    @Test void corruptAndFutureRecordsArePreserved() throws Exception {
        var store=new TravelJournalStore(directory);var path=directory.resolve("travel-journal.json");
        for(String invalid:List.of("{bad json","{\"version\":2,\"entries\":[]}","{\"version\":1,\"entries\":null}")) {
            Files.writeString(path,invalid);assertThrows(java.io.IOException.class,store::load);assertEquals(invalid,Files.readString(path));
        }
    }
    @Test void iconAndTeleportMetadataDoNotInventAWaypoint() {
        var venue=new GuideVenue(a,"馆","",0,0,0,Float.NaN,Float.NaN);assertFalse(venue.canTeleport());
        assertEquals("exhibition_portal:textures/gui/units/"+a.toString().replace("-", "")+"/icon.png",venue.icon());
    }
}
