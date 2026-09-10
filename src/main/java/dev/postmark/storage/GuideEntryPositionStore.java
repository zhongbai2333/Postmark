package dev.postmark.storage;

import com.google.gson.Gson;
import dev.postmark.model.GuideEntryPosition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Instance-wide UI preference; never stored in a player's album. */
public final class GuideEntryPositionStore {
    private static final Gson JSON=new Gson();
    private final Path file;
    public GuideEntryPositionStore(Path gameDirectory) {file=gameDirectory.resolve("postmark/guide-entry-position.json");}
    public GuideEntryPosition load() throws IOException {
        if(!Files.exists(file))return null;
        try {return JSON.fromJson(Files.readString(file),GuideEntryPosition.class);}
        catch(RuntimeException e) {throw new IOException("Invalid guide entry position",e);}
    }
    public void save(GuideEntryPosition position) throws IOException {
        AlbumStore.writeAtomic(file,JSON.toJson(position).getBytes(StandardCharsets.UTF_8));
    }
}
