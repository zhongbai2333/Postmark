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
        assertEquals(74,catalog.size());assertEquals("2026-09-11",catalog.date());
        assertEquals("MCP",catalog.entry(mcp).name());assertNull(catalog.entry(UUID.randomUUID()));
        assertEquals("Mino++",catalog.entry(UUID.fromString("01210ebf-d7c9-5eb9-83cd-bdd1f80fcf88")).name()); // player export resolves grouped E7
    }
    @Test void presetDoesNotGrantOwnershipSurveyOrArtwork() {
        var journal=TravelJournal.empty();var merged=catalog.merge(mcp,journal.stamps(mcp,List.of()));
        assertEquals(1,merged.size());assertEquals("visitor",merged.getFirst().id());
        assertFalse(merged.getFirst().owned());assertNull(merged.getFirst().item());assertNull(merged.getFirst().asset());
        assertFalse(journal.entry(mcp).searched());assertTrue(journal.entries().isEmpty());assertFalse(catalog.complete(mcp,List.of()));
    }
    @Test void importedCatalogOnlyProvidesSilhouettesForAllSeventyFourVenues() throws Exception {
        try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/assets/postmark/catalogs/teacon2026.json"),java.nio.charset.StandardCharsets.UTF_8)) {
            var root=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();int confirmed=0;
            for(var value:root.getAsJsonArray("entries")) {
                var row=value.getAsJsonObject();if(!row.has("venue"))continue;
                var id=UUID.fromString(row.get("venue").getAsString());var display=catalog.display(id,List.of(),false);
                assertEquals(row.has("confirmedArtworks")?row.getAsJsonArray("confirmedArtworks").size():row.getAsJsonArray("confirmedByExport").size(),display.size());
                for(var stamp:display){assertNull(stamp.item());assertNull(stamp.asset());assertFalse(stamp.owned());}
                assertTrue(catalog.needsInspection(id,false,List.of(),false));assertFalse(catalog.complete(id,List.of(),false));
                confirmed+=display.size();
                assertFalse(row.has("stamps"));assertFalse(row.has("searched"));assertFalse(row.has("allKnownCollected"));
            }
            assertEquals(113,confirmed);
        }
    }
    @Test void discoveryRevealsArtworkAndCollectionLightsItWithoutWaitingForArea() {
        for(String kind:List.of("visitor","expert")) {
            String json="{\"version\":1,\"date\":\"test\",\"entries\":[{\"venue\":\""+mcp+"\",\"sourceRow\":0,\"name\":\"single\",\"visitor\":\""+(kind.equals("visitor")?"PRESENT":"ABSENT")+"\",\"expert\":\""+(kind.equals("expert")?"PRESENT":"ABSENT")+"\",\"confirmedByExport\":[\""+kind+"\"]}]}";
            var single=StampCatalog.read(new java.io.StringReader(json));
            var initial=single.display(mcp,List.of(),false);assertEquals(1,initial.size());assertNull(initial.getFirst().item());
            var seen=List.of(stamp(kind,false));assertEquals(seen,single.display(mcp,seen,false));
            assertFalse(single.complete(mcp,seen,false));
            var owned=List.of(stamp(kind,true));assertTrue(single.complete(mcp,owned,false));
            assertFalse(single.needsInspection(mcp,false,owned,false));
            var additional=List.of(stamp(kind,true),stamp(kind.equals("visitor")?"expert":"visitor",false));
            assertEquals(2,single.display(mcp,additional,true).size());assertFalse(single.complete(mcp,additional,true));
        }
        assertEquals(2,catalog.display(UUID.randomUUID(),List.of(),false).size());
    }
    @Test void confirmedSingleCompletesImmediatelyAndPairWaitsForBoth() {
        var visitor=List.of(stamp("visitor",true));assertTrue(catalog.complete(mcp,visitor));assertTrue(catalog.complete(sky,visitor));
        var paired=UUID.fromString("bf52f38c-3531-54fa-a83c-97eeafd51b54");assertFalse(catalog.complete(paired,visitor,true));
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
    @Test void UnsearchedVenuesKeepQuestionUntilActualCollectionCompletes() {
        assertTrue(catalog.needsInspection(mcp,false,List.of()));
        assertFalse(catalog.needsInspection(sky,false,List.of(stamp("visitor",true),stamp("expert",true))));
        assertTrue(catalog.needsInspection(UUID.randomUUID(),false,List.of()));
    }
    @Test void EmptyOrPartialScanCannotEraseUnknownOrKnownUncollectedSlots() {
        assertTrue(catalog.needsInspection(UUID.randomUUID(),true,List.of()));
        assertTrue(catalog.needsInspection(mcp,true,List.of())); // ordinary is known to exist
        assertTrue(catalog.needsInspection(UUID.fromString("bf52f38c-3531-54fa-a83c-97eeafd51b54"),true,List.of(stamp("visitor",false)),true)); // confirmed master cannot disappear after area scan
        assertEquals(1,catalog.merge(mcp,List.of()).size());
    }
    @Test void QuestionClearsWithObservedPairOrCompletedAreaWithoutClaimingOwnership() {
        assertFalse(catalog.needsInspection(mcp,true,List.of(stamp("visitor",false))));
        assertFalse(catalog.needsInspection(mcp,true,List.of(stamp("visitor",false)),true));
        assertFalse(catalog.needsInspection(sky,true,List.of(stamp("visitor",false),stamp("expert",false))));
        assertFalse(catalog.complete(mcp,List.of(stamp("visitor",false))));
        assertFalse(catalog.complete(sky,List.of(stamp("visitor",false),stamp("expert",false))));
    }
    @Test void anvilVariantsStayHiddenUntilFoundAndBothAreRequired() {
        var venue=UUID.fromString("784b34de-ac17-567f-a9f0-bcc1853a1a5b");
        var hidden=catalog.display(venue,List.of(),false);
        assertEquals(2,hidden.size());assertEquals(2,hidden.stream().map(TravelJournal.KnownStamp::identity).distinct().count());
        assertTrue(hidden.stream().allMatch(s->s.item()==null && s.asset()==null && !s.owned()));
        var first=new TravelJournal.KnownStamp("visitor","anvilcraft:spacetime_supercomputer","a",true);
        var second=new TravelJournal.KnownStamp("visitor","anvilcraft:celestial_forging_anvil",null,false);
        assertFalse(catalog.complete(venue,List.of(first),true));
        assertTrue(catalog.needsInspection(venue,true,List.of(first),true));
        assertEquals(2,catalog.display(venue,List.of(first,second),true).size());
        assertFalse(catalog.needsInspection(venue,true,List.of(first,second),true));
        var both=List.of(first,new TravelJournal.KnownStamp(second.id(),second.item(),"b",true));
        assertTrue(catalog.complete(venue,both,false));assertTrue(catalog.complete(venue,both,true));
        var three=new ArrayList<>(both);three.add(new TravelJournal.KnownStamp("visitor","minecraft:paper",null,false));
        assertEquals(3,catalog.display(venue,three,true).size());assertFalse(catalog.complete(venue,three,true));
    }
}
