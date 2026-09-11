package dev.postmark;

import dev.postmark.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GuideSearchTest {
    @Test void searchesVenueAndAssociatedModAliasesWithCaseAndSpacingNormalized() {
        var venue=new GuideVenue(UUID.fromString("784b34de-ac17-567f-a9f0-bcc1853a1a5b"),"铁砧工艺：锻星之旅","",0,0,0,0,0);
        var mods=StampCatalog.bundled().mods(venue.id());
        assertTrue(GuideSearch.matches(venue,mods,"锻星"));assertTrue(GuideSearch.matches(venue,mods,"ANVILcraft"));
        assertTrue(GuideSearch.matches(venue,mods,"anvil craft"));assertTrue(GuideSearch.matches(venue,mods,"ＡＮＶＩＬ"));
        assertFalse(GuideSearch.matches(venue,mods,"anvil nonexistent"));assertTrue(GuideSearch.matches(venue,mods," "));
    }
}
