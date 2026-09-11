package dev.postmark.storage;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class GuideRevealsStore {
    private final Path path;
    public GuideRevealsStore(Path directory){path=directory.resolve("guide-reveals.json");}
    public Set<String> load() throws IOException {
        if(!Files.exists(path))return Set.of();
        if(Files.size(path)>1024*1024)throw new IOException("Guide reveal history too large");
        try {
            var result=new HashSet<String>();for(var value:JsonParser.parseString(Files.readString(path)).getAsJsonArray())result.add(value.getAsString());return Set.copyOf(result);
        }catch(RuntimeException e){throw new IOException("Invalid reveal history; original preserved",e);}
    }
    public void save(Set<String> keys) throws IOException {AlbumStore.writeAtomic(path,new Gson().toJson(new TreeSet<>(keys)).getBytes(StandardCharsets.UTF_8));}
}
