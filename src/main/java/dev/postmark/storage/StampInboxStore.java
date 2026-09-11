package dev.postmark.storage;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Pending presentation only; this file can never grant ownership. */
public final class StampInboxStore {
    private final Path path;
    public StampInboxStore(Path directory){path=directory.resolve("stamp-inbox.json");}
    public List<String> load() throws IOException {
        if(!Files.exists(path))return List.of();
        if(Files.size(path)>1024*1024)throw new IOException("Stamp inbox too large");
        try{var keys=new LinkedHashSet<String>();for(var value:JsonParser.parseString(Files.readString(path)).getAsJsonArray())keys.add(value.getAsString());return List.copyOf(keys);}
        catch(RuntimeException e){throw new IOException("Invalid stamp inbox; original preserved",e);}
    }
    public void save(Collection<String> keys) throws IOException {AlbumStore.writeAtomic(path,new Gson().toJson(new ArrayList<>(new LinkedHashSet<>(keys))).getBytes(StandardCharsets.UTF_8));}
}
