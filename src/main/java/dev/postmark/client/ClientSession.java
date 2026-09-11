package dev.postmark.client;

import dev.postmark.model.*;
import dev.postmark.render.PostcardPainter;
import dev.postmark.storage.AlbumStore;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.Path;

public final class ClientSession {
    private static ClientSession active;
    private final String scope;
    private final AlbumStore store;
    private Album album;
    private TravelJournal journal;
    private java.util.Set<String> guideReveals;
    private boolean revealsDirty;
    private java.util.List<String> stampInbox;
    public java.util.List<String> stampInbox() throws IOException {
        if(stampInbox==null)stampInbox=new dev.postmark.storage.StampInboxStore(store.directory()).load();
        return stampInbox;
    }
    public void enqueueStamps(java.util.Collection<String> keys) throws IOException {
        var next=new java.util.LinkedHashSet<>(stampInbox());next.addAll(keys);saveInbox(next);
    }
    public void consumeStamps(java.util.Collection<String> keys) throws IOException {
        var next=new java.util.LinkedHashSet<>(stampInbox());next.removeAll(keys);saveInbox(next);
    }
    private void saveInbox(java.util.Collection<String> keys) throws IOException {
        var owned=new java.util.HashSet<String>();for(var s:album.stamps())if(!s.practice())owned.add(s.key());
        var next=keys.stream().filter(owned::contains).distinct().toList();if(next.equals(stampInbox))return;
        new dev.postmark.storage.StampInboxStore(store.directory()).save(next);stampInbox=next;
    }
    public java.util.Set<String> guideReveals() throws IOException {
        if(guideReveals==null)guideReveals=new java.util.HashSet<>(new dev.postmark.storage.GuideRevealsStore(store.directory()).load());
        return java.util.Set.copyOf(guideReveals);
    }
    public void revealGuideStamps(java.util.Collection<String> keys) throws IOException {guideReveals();if(guideReveals.addAll(keys))revealsDirty=true;}
    public void flushGuideReveals() throws IOException {if(revealsDirty){new dev.postmark.storage.GuideRevealsStore(store.directory()).save(guideReveals);revealsDirty=false;}}
    private ClientSession(String scope, AlbumStore store) throws IOException {
        this.scope = scope; this.store = store; album = store.load();
        // Retire only the two built-in shelf entries. Existing imprints and their assets remain intact.
        album=new Album(album.version(),album.current(),album.cards(),album.stamps().stream()
                .filter(stamp->!stamp.practice() || (!stamp.key().equals("practice:1") && !stamp.key().equals("practice:2"))).toList());
        String asset=store.putImage(PostcardPainter.practice(0));
        if(album.stamps().stream().noneMatch(stamp->stamp.key().equals("practice:0") && stamp.asset().equals(asset)))
            album=album.unlock(new StampDefinition("practice:0","启程 / BON VOYAGE",asset,true));
        store.save(album);
    }
    public static String scope() {
        var mc = Minecraft.getInstance();
        String world = mc.getCurrentServer() != null ? "server:" + mc.getCurrentServer().ip
                : mc.getSingleplayerServer() != null ? "world:" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                : "studio";
        return mc.getUser().getProfileId() + "/" + world;
    }
    public static ClientSession get() throws IOException {
        String scope = scope();
        if (active == null || !active.scope.equals(scope)) {
            Path root = Minecraft.getInstance().gameDirectory.toPath().resolve("postmark");
            active = new ClientSession(scope, new AlbumStore(root, scope));
        }
        return active;
    }
    public static void clear() { active = null; }
    public Album album() { return album; }
    public AlbumStore store() { return store; }
    public TravelJournal journal() throws IOException {
        if(journal==null)journal=new dev.postmark.storage.TravelJournalStore(store.directory()).load();
        return journal;
    }
    public GuideViewport.Bookmark guideView() throws IOException {return new dev.postmark.storage.GuideViewStore(store.directory()).load();}
    public void saveGuideView(GuideViewport.Bookmark view) throws IOException {new dev.postmark.storage.GuideViewStore(store.directory()).save(view);}
    public void updateJournal(TravelJournal next) throws IOException {
        if(next.equals(journal()))return;
        new dev.postmark.storage.TravelJournalStore(store.directory()).save(next);journal=next;
    }
    public void update(Album next) throws IOException {
        store.save(next); // Commit the in-memory state only after the atomic save succeeds.
        album = next;
        try {
            guideReveals();var keys=new java.util.HashSet<String>();for(var stamp:next.stamps())keys.add(stamp.key());
            if(guideReveals.retainAll(keys)){revealsDirty=true;flushGuideReveals();}
            saveInbox(stampInbox());
        } catch(IOException e){dev.postmark.Postmark.LOGGER.warn("Cannot update guide reveal history",e);}
    }
}
