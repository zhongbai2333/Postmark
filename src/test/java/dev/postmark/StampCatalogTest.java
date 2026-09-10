package dev.postmark;

import dev.postmark.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StampCatalogTest {
    private final StampCatalog catalog=StampCatalog.bundled();
    private final UUID mcp=UUID.fromString("b8509a22-7c38-593d-b3fc-e2a2fe13bbe4");
    private final UUID sky=UUID.fromString("493f4bbe-20c0-5d8b-bf29-ac3ce6d84076");
    private TravelJournal.KnownStamp stamp(String id,boolean owned) {return new TravelJournal.KnownStamp(id,"minecraft:paper",owned?"image.png":null,owned);}
    @Test void resourceMapsOnlyVerifiedIdentities() {
        assertEquals(66,catalog.size());assertEquals("2026-09-10",catalog.date());
        assertEquals("MCP",catalog.entry(mcp).name());assertNull(catalog.entry(UUID.randomUUID()));
        assertNull(catalog.entry(UUID.fromString("01210ebf-d7c9-5eb9-83cd-bdd1f80fcf88"))); // grouped E7
    }
    @Test void presetDoesNotGrantOwnershipSurveyOrArtwork() {
        var journal=TravelJournal.empty();var merged=catalog.merge(mcp,journal.stamps(mcp,List.of()));
        assertEquals(1,merged.size());assertEquals("visitor",merged.getFirst().id());
        assertFalse(merged.getFirst().owned());assertNull(merged.getFirst().item());assertNull(merged.getFirst().asset());
        assertFalse(journal.entry(mcp).searched());assertTrue(journal.entries().isEmpty());assertFalse(catalog.complete(mcp,List.of()));
    }
    @Test void explicitSingleStampCompletesButBlankMasterRemainsUnknown() {
        var visitor=List.of(stamp("visitor",true));assertTrue(catalog.complete(mcp,visitor));assertFalse(catalog.complete(sky,visitor));
        assertTrue(catalog.complete(sky,List.of(stamp("visitor",true),stamp("expert",true))));
    }
    @Test void observedExtraAndContradictedAbsenceRevokeCompletion() {
        for(String id:List.of("expert","special")) {
            var observed=List.of(stamp("visitor",true),stamp(id,false));
            assertEquals(2,catalog.merge(mcp,observed).size());assertFalse(catalog.complete(mcp,observed));
            assertTrue(catalog.complete(mcp,List.of(stamp("visitor",true),stamp(id,true))));
        }
    }
    @Test void actualArtworkOverridesGhostAndStylesDoNotMultiplySlots() {
        var id=UUID.fromString("fd5b627b-2be2-58f7-b10c-93c269e9d44e");
        var merged=catalog.merge(id,List.of(stamp("visitor",false)));
        assertEquals(2,merged.size());assertEquals("minecraft:paper",merged.stream().filter(s->s.id().equals("visitor")).findFirst().orElseThrow().item());
        assertFalse(catalog.complete(id,List.of(stamp("visitor",true))));
    }
    @Test void UnmatchedVenuesRetainDiscoveryBehavior() {
        var id=UUID.randomUUID();assertTrue(catalog.merge(id,List.of()).isEmpty());
        assertFalse(catalog.complete(id,List.of(stamp("visitor",true))));
        assertTrue(catalog.complete(id,List.of(stamp("visitor",true),stamp("expert",true))));
    }
}
