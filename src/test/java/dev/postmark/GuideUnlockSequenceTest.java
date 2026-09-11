package dev.postmark;

import dev.postmark.model.GuideUnlockSequence;
import dev.postmark.storage.GuideRevealsStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GuideUnlockSequenceTest {
    @TempDir Path folder;
    @Test void visibleStampsUnlockInOrderAndPersistOnlyAfterFinishing() throws Exception {
        var q=new GuideUnlockSequence(Set.of());q.sync(List.of("a","b","offscreen"));
        assertTrue(q.advance(0,List.of("a","b")).isEmpty());assertEquals(0,q.amount("b",100));assertTrue(q.amount("a",100)>0);
        q.advance(220,List.of("a","b"));assertTrue(q.amount("b",300)>0);assertEquals(0,q.amount("offscreen",900));
        assertEquals(List.of("a"),q.advance(550,List.of()));assertEquals(List.of("b"),q.advance(770,List.of()));
        var store=new GuideRevealsStore(folder);store.save(Set.of("a","b"));
        var reopened=new GuideUnlockSequence(store.load());reopened.sync(List.of("a","b","offscreen"));
        assertFalse(reopened.pending("a"));assertTrue(reopened.pending("offscreen"));
    }
    @Test void closingMidwayDoesNotSkipAnimationAndResetAllowsRecollection() {
        var q=new GuideUnlockSequence(Set.of("old"));q.sync(List.of("old","new"));q.advance(0,List.of("new"));
        q.sync(List.of());assertEquals(0,q.activeCount());q.sync(List.of("old"));assertTrue(q.pending("old"));
    }
}
