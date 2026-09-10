package dev.postmark.compat;

import dev.postmark.client.ClientSession;
import dev.postmark.model.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.*;
import static dev.postmark.compat.SignMeUpBridge.call;

/** One loaded chunk per tick, no chunk requests, and an atomic journal update after full coverage. */
final class GuideSurvey {
    private ClientLevel level;
    private ClientSession session;
    private BlockPos origin;
    private int chunk,wait;
    private final List<TravelJournal.Discovery> found=new ArrayList<>();
    private final Set<UUID> searched=new HashSet<>();
    void reset() {level=null;session=null;origin=null;chunk=0;wait=40;found.clear();searched.clear();}
    private LevelChunk loaded(int i) {
        return level.getChunkSource().getChunk((origin.getX()>>4)+i%5-2,(origin.getZ()>>4)+i/5-2,ChunkStatus.FULL,false);
    }
    private boolean ready() {for(int i=0;i<25;i++)if(loaded(i)==null)return false;return true;}
    void tick(List<GuideVenue> venues) throws Exception {
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null || venues.isEmpty()) {reset();return;}
        if(level==null) {
            if(wait-->0)return;
            level=mc.level;origin=mc.player.blockPosition();session=ClientSession.get();chunk=0;found.clear();searched.clear();
            if(!ready()) {reset();return;}
            if(level.dimension().equals(Level.OVERWORLD)) venues.stream().filter(GuideVenue::canTeleport)
                    .filter(v->v.distanceSquared(mc.player.getX(),mc.player.getZ())<=24*24)
                    .min(Comparator.comparingDouble(v->v.distanceSquared(mc.player.getX(),mc.player.getZ())))
                    .ifPresent(v->searched.add(v.id()));
        }
        if(mc.level!=level || session!=ClientSession.get() || origin.distSqr(mc.player.blockPosition())>64 || !ready()) {reset();return;}
        // Only data already present in this client's chunks is inspected.
        for(var entity:loaded(chunk).getBlockEntities().values()) {
            if(!entity.getClass().getName().equals("org.teacon.exhibition_portal.client.interaction.StampingCounterBlockEntity"))continue;
            var pos=entity.getBlockPos();if(Math.abs(pos.getX()-origin.getX())>32 || Math.abs(pos.getZ()-origin.getZ())>32)continue;
            UUID id=(UUID)call(entity,"getExhibition");String stamp=(String)call(entity,"getStampID");Object item=call(entity,"getItem");
            if(id==null || stamp==null || item==null || venues.stream().noneMatch(v->v.id().equals(id)))continue;
            found.add(new TravelJournal.Discovery(id,stamp,item.toString()));searched.add(id);
        }
        if(++chunk==25) {
            // Never infer "this venue has zero stamps" from an empty successful pass.
            if(!searched.isEmpty())session.updateJournal(session.journal().surveyed(searched,found));
            reset();
        }
    }
}
