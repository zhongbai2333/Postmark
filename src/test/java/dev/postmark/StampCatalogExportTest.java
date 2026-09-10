package dev.postmark;

import com.google.gson.JsonParser;
import dev.postmark.model.StampCatalogExport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StampCatalogExportTest {
    @TempDir Path directory;
    @Test void exportDistinguishesOwnedAndObservedWithoutClaimingCatalogCompleteness() throws Exception {
        var id=UUID.randomUUID();
        var venue=new StampCatalogExport.Venue(id,"测试馆",true,true,false,List.of(
                new StampCatalogExport.Stamp("expert","minecraft:diamond","server_owned"),
                new StampCatalogExport.Stamp("visitor","minecraft:paper","local_discovered")));
        var data=StampCatalogExport.create("0.1.1",List.of(venue));assertEquals(1,data.ownedCount());
        var path=data.write(directory);var root=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals(1,root.get("formatVersion").getAsInt());assertFalse(root.get("completeCatalog").getAsBoolean());
        assertEquals(id.toString(),root.getAsJsonArray("venues").get(0).getAsJsonObject().get("uuid").getAsString());
        assertEquals(2,root.getAsJsonArray("venues").get(0).getAsJsonObject().getAsJsonArray("stamps").size());
        assertNotEquals(path,data.write(directory));assertTrue(Files.exists(path));
        assertEquals(Set.of("formatVersion","generatedAt","modVersion","completeCatalog","venues"),root.keySet());
    }
}
