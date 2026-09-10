package dev.postmark.model;

import com.google.gson.GsonBuilder;
import dev.postmark.storage.AlbumStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** Shareable observations, without player identity, server address, or private artwork. */
public record StampCatalogExport(int formatVersion,String generatedAt,String modVersion,boolean completeCatalog,List<Venue> venues) {
    public record Stamp(String id,String item,String source) {}
    public record Venue(UUID uuid,String name,boolean searched,boolean areaSearched,boolean allKnownCollected,List<Stamp> stamps) {
        public Venue {stamps=List.copyOf(stamps);}
    }
    public StampCatalogExport {venues=List.copyOf(venues);}
    public static StampCatalogExport create(String modVersion,List<Venue> venues) {
        // A collection export cannot establish that an unobserved stamp does not exist.
        return new StampCatalogExport(1,Instant.now().toString(),modVersion,false,venues);
    }
    public long ownedCount() {return venues.stream().flatMap(v->v.stamps.stream()).filter(s->s.source.equals("server_owned")).count();}
    public Path write(Path directory) throws IOException {
        Path path=directory.resolve("stamp-catalog-"+Instant.now().toEpochMilli()+"-"+UUID.randomUUID().toString().substring(0,8)+".json");
        byte[] data=new GsonBuilder().setPrettyPrinting().create().toJson(this).getBytes(StandardCharsets.UTF_8);
        AlbumStore.writeAtomic(path,data);return path;
    }
}
