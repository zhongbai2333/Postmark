package dev.postmark.storage;

import com.google.gson.Gson;
import dev.postmark.model.GuideViewport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GuideViewStore {
    private static final Gson JSON=new Gson();
    private final Path file;
    public GuideViewStore(Path playerDirectory) {file=playerDirectory.resolve("guide-view.json");}
    public GuideViewport.Bookmark load() throws IOException {
        if(!Files.exists(file))return null;
        try {return JSON.fromJson(Files.readString(file),GuideViewport.Bookmark.class);}
        catch(RuntimeException e) {throw new IOException("Invalid guide view",e);}
    }
    public void save(GuideViewport.Bookmark view) throws IOException {AlbumStore.writeAtomic(file,JSON.toJson(view).getBytes(StandardCharsets.UTF_8));}
}
