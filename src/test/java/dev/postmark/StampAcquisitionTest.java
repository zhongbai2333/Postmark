package dev.postmark;

import dev.postmark.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StampAcquisitionTest {
    private final UUID venue=UUID.randomUUID();
    private StampDefinition stamp(String item,String asset) {return new StampDefinition(StampIdentity.key(venue,"visitor",item),"普通",asset,false);}
    @Test void waitsForActualCollectionAndRevealsOnlyOnce() {
        var s=stamp("minecraft:paper","a");var observer=new StampAcquisition(s.key(),List.of());
        assertNull(observer.poll(List.of()));assertEquals(s,observer.poll(List.of(s)));assertNull(observer.poll(List.of(s)));
    }
    @Test void reopeningOrRenamingOwnedStampDoesNotReplay() {
        var s=stamp("minecraft:paper","a");var observer=new StampAcquisition(s.key(),List.of(s));
        assertNull(observer.poll(List.of(new StampDefinition(s.key(),"新名字","b",false))));
        assertNull(new StampAcquisition(null,List.of()).poll(List.of(s)));
    }
    @Test void distinguishesSecondSameIdArtworkFromLegacyMigration() {
        var a=stamp("minecraft:paper","a");var b=stamp("minecraft:stone","b");
        assertEquals(b,new StampAcquisition(b.key(),List.of(a)).poll(List.of(a,b)));
        var legacy=new StampDefinition(venue+"/visitor","旧章","a",false);
        assertNull(new StampAcquisition(a.key(),List.of(legacy)).poll(List.of(a)));
        assertEquals(b,new StampAcquisition(b.key(),List.of(legacy)).poll(List.of(legacy,b)));
    }
    @Test void resetAllowsRecollectionAndUnrelatedSyncDoesNotTrigger() {
        var a=stamp("minecraft:paper","a");var b=stamp("minecraft:stone","b");
        var observer=new StampAcquisition(a.key(),List.of());assertNull(observer.poll(List.of(b)));
        assertEquals(a,observer.poll(List.of(a,b)));
    }
}
