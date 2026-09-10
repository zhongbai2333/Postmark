package dev.postmark.compat;

import dev.postmark.client.ClientSession;
import dev.postmark.model.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.*;
import static dev.postmark.compat.SignMeUpBridge.call;

/** Incremental inspection of client-loaded chunks; never requests or generates chunks. */
final class GuideSurvey {
    private static final int CHUNKS_PER_TICK=4,FLUSH_TICKS=20,REPEAT_DELAY=40;
    private ClientLevel level;
    private ClientSession session;
    private final Map<net.minecraft.world.level.ChunkPos,LevelChunk> loaded=new LinkedHashMap<>();
    private final ArrayDeque<LevelChunk> pending=new ArrayDeque<>();
    private int wait,flush;
    private final List<TravelJournal.Discovery> found=new ArrayList<>();
    private final Set<UUID> searched=new HashSet<>();
    // A teleport restarts scheduling, but does not forget chunks that remain loaded.
    void reset() {session=null;pending.clear();wait=REPEAT_DELAY;flush=0;found.clear();searched.clear();}
    void clear() {reset();level=null;loaded.clear();}
    void loaded(LevelChunk chunk) {
        if(!(chunk.getLevel() instanceof ClientLevel client))return;
        if(level!=client) {clear();level=client;}
        loaded.put(chunk.getPos(),chunk);
    }
    void unloaded(LevelChunk chunk) {
        if(chunk.getLevel()==level)loaded.remove(chunk.getPos(),chunk);
    }
    void tick(List<GuideVenue> venues) throws Exception {
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null) {clear();return;}
        if(level!=mc.level) {clear();level=mc.level;}
        if(venues.isEmpty())return;
        var current=ClientSession.get();
        if(session!=current) {reset();session=current;}
        if(pending.isEmpty()) {
            if(wait-->0)return;
            pending.addAll(loaded.values());
            if(pending.isEmpty()) {wait=REPEAT_DELAY;return;}
        }
        var venueIds=new HashSet<UUID>();for(var venue:venues)venueIds.add(venue.id());
        for(int i=0;i<CHUNKS_PER_TICK && !pending.isEmpty();i++) {
            var chunk=pending.removeFirst();var pos=chunk.getPos();
            // Radius changes can discard chunks without an unload event. Validate actual cache identity.
            if(loaded.get(pos)!=chunk)continue;
            if(level.getChunkSource().getChunk(pos.x(),pos.z(),ChunkStatus.FULL,false)!=chunk) {
                loaded.remove(pos,chunk);continue;
            }
            for(var entity:chunk.getBlockEntities().values()) {
                if(!entity.getClass().getName().equals("org.teacon.exhibition_portal.client.interaction.StampingCounterBlockEntity"))continue;
                UUID id=(UUID)call(entity,"getExhibition");String stamp=(String)call(entity,"getStampID");Object item=call(entity,"getItem");
                if(id==null || stamp==null || stamp.isBlank() || item==null || !venueIds.contains(id))continue;
                found.add(new TravelJournal.Discovery(id,stamp,item.toString()));searched.add(id);
            }
            // An empty inspection may leave a footprint at the player's nearby venue, never invent stamps.
            if(level.dimension().equals(Level.OVERWORLD) && pos.equals(mc.player.chunkPosition())) venues.stream().filter(GuideVenue::canTeleport)
                    .filter(v->v.distanceSquared(mc.player.getX(),mc.player.getZ())<=24*24)
                    .min(Comparator.comparingDouble(v->v.distanceSquared(mc.player.getX(),mc.player.getZ())))
                    .ifPresent(v->searched.add(v.id()));
        }
        if(++flush>=FLUSH_TICKS || pending.isEmpty()) {
            if(!searched.isEmpty())session.updateJournal(session.journal().surveyed(searched,found));
            found.clear();searched.clear();flush=0;
        }
        if(pending.isEmpty())wait=REPEAT_DELAY;
    }
}
