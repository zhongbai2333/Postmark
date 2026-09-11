package dev.postmark;

import dev.postmark.storage.StampInboxStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StampInboxStoreTest {
    @TempDir Path folder;
    @Test void queuePreservesAcquisitionOrderDeduplicatesAndSurvivesReopen() throws Exception {
        var store=new StampInboxStore(folder);assertTrue(store.load().isEmpty());
        store.save(List.of("second","first","second"));assertEquals(List.of("second","first"),new StampInboxStore(folder).load());
        store.save(List.of("first"));assertEquals(List.of("first"),store.load());
    }
    @Test void corruptQueueIsNeverSilentlyOverwritten() throws Exception {
        Files.writeString(folder.resolve("stamp-inbox.json"),"broken");
        assertThrows(java.io.IOException.class,()->new StampInboxStore(folder).load());
        assertEquals("broken",Files.readString(folder.resolve("stamp-inbox.json")));
    }
}
