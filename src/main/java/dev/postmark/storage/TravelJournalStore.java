package dev.postmark.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.postmark.model.TravelJournal;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class TravelJournalStore {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    public TravelJournalStore(Path directory) { path=directory.resolve("travel-journal.json"); }
    public TravelJournal load() throws IOException {
        if(!Files.exists(path))return TravelJournal.empty();
        if(Files.size(path)>8*1024*1024)throw new IOException("Travel journal exceeds 8 MB");
        try(var reader=Files.newBufferedReader(path)) {
            var result=JSON.fromJson(reader,TravelJournal.class);
            if(result==null)throw new IOException("Empty travel journal; original file preserved");
            return result;
        } catch(RuntimeException e) {throw new IOException("Cannot read travel journal; original file preserved",e);}
    }
    public void save(TravelJournal journal) throws IOException {
        byte[] bytes=JSON.toJson(journal).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>8*1024*1024)throw new IOException("Travel journal exceeds 8 MB; previous file preserved");
        AlbumStore.writeAtomic(path,bytes);
    }
}
