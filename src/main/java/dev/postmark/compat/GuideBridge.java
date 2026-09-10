package dev.postmark.compat;

import dev.postmark.Postmark;
import dev.postmark.client.ClientSession;
import dev.postmark.model.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.fml.ModList;
import java.util.*;
import static dev.postmark.compat.SignMeUpBridge.call;

/** Optional reflection boundary: no SMU class is linked by Postmark's release jar. */
public final class GuideBridge {
    private static List<GuideVenue> venues=List.of();
    private static Object gallery,connection;
    private static int ticks;
    private static String status="";
    private static final GuideSurvey SURVEY=new GuideSurvey();
    private GuideBridge() {}
    public static void chunkLoaded(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        if(available() && event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk && chunk.getLevel().isClientSide())SURVEY.loaded(chunk);
    }
    public static void chunkUnloaded(net.neoforged.neoforge.event.level.ChunkEvent.Unload event) {
        if(available() && event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk && chunk.getLevel().isClientSide())SURVEY.unloaded(chunk);
    }
    public static List<GuideVenue> venues() { return venues; }
    public static String status() { return status; }
    public static boolean available() { return ModList.get().isLoaded("exhibition_portal"); }
    public static void tick() {
        var mc=Minecraft.getInstance();ticks++;
        if(connection!=mc.getConnection() || mc.level==null) {
            connection=mc.getConnection();gallery=null;venues=List.of();status="";if(mc.level==null)SURVEY.clear();else SURVEY.reset();
        }
        if(mc.level==null || mc.player==null || !available())return;
        try {
            if(ticks%20==0)refresh();
            SURVEY.tick(venues);
        } catch(Exception e) {
            if(status.isEmpty())Postmark.LOGGER.warn("Travel guide synchronization failed",e);
            status="导览暂不可用："+e.getMessage();SURVEY.reset();
        }
    }
    private static void refresh() throws Exception {
        var next=SignMeUpBridge.gallery();if(next==gallery)return;
        var result=new ArrayList<GuideVenue>();
        for(var exhibition:next.values()) {
            var metadata=call(exhibition,"metadata");var waypoint=call(metadata,"waypoint");
            String name=call(metadata,"name").toString();
            result.add(new GuideVenue((UUID)call(exhibition,"uuid"),name.isBlank() || name.equals("@unset")?"未命名展馆":name,
                    call(metadata,"description").toString(),(int)call(waypoint,"x"),(int)call(waypoint,"y"),(int)call(waypoint,"z"),
                    (float)call(waypoint,"ry"),(float)call(waypoint,"rx")));
        }
        result.sort(Comparator.comparing(v->v.id().toString()));
        venues=List.copyOf(result);gallery=next;status="";
    }
    public static boolean teleport(UUID id) {
        var mc=Minecraft.getInstance();var venue=venues.stream().filter(v->v.id().equals(id)).findFirst().orElse(null);
        if(mc.player==null || mc.getConnection()==null || venue==null || !venue.canTeleport()) {status="这个展馆还没有可用的传送点";return false;}
        try {
            var type=Class.forName("org.teacon.exhibition_portal.network.TeleportToExhibitionPacket");
            mc.getConnection().send((CustomPacketPayload)type.getConstructor(UUID.class).newInstance(id));
            // Sending does not mark searched. Only GuideSurvey can write that evidence.
            SURVEY.reset();status="";return true;
        } catch(Exception e) {status="传送请求失败："+e.getMessage();Postmark.LOGGER.warn("SMU guide teleport failed",e);return false;}
    }
}
