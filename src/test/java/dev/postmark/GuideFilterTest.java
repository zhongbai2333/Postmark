package dev.postmark;

import dev.postmark.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GuideFilterTest {
    @Test void missingIncludesUnknownAndDifferentUnownedArtworksButNotEmptySurvey() {
        var owned=new TravelJournal.KnownStamp("visitor","minecraft:paper","a",true);
        var unknown=new TravelJournal.KnownStamp("visitor",null,null,false,"minecraft:stone");
        assertTrue(GuideFilter.MISSING.includes(false,List.of(unknown)));
        assertTrue(GuideFilter.MISSING.includes(true,List.of(owned,unknown)));
        assertFalse(GuideFilter.MISSING.includes(true,List.of(owned)));
        assertFalse(GuideFilter.MISSING.includes(true,List.of()));
    }
    @Test void unvisitedUsesSurveyFootprintIndependentlyOfCollection() {
        assertTrue(GuideFilter.UNSEARCHED.includes(false,List.of()));
        assertFalse(GuideFilter.UNSEARCHED.includes(true,List.of(new TravelJournal.KnownStamp("visitor",null,null,false))));
        assertTrue(GuideFilter.ALL.includes(true,List.of()));
    }
    @Test void multipleCounterLocationsForOneArtworkNeverCreateMoreStamps() {
        var venue=UUID.randomUUID();
        var a=new TravelJournal.Discovery(venue,"visitor","minecraft:paper");
        var journal=TravelJournal.empty().surveyed(Set.of(venue),List.of(a,a,a));
        assertEquals(1,journal.stamps(venue,List.of()).size());
        journal=journal.surveyed(Set.of(venue),List.of(a,new TravelJournal.Discovery(venue,"visitor","minecraft:stone"),a));
        assertEquals(2,journal.stamps(venue,List.of()).size());
    }
}
