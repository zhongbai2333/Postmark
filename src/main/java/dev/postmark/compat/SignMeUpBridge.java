package dev.postmark.compat;

import com.google.gson.JsonParser;
import dev.postmark.Postmark;
import dev.postmark.client.ClientSession;
import dev.postmark.client.PostcardScreen;
import dev.postmark.model.MapPlacement;
import dev.postmark.model.StampDefinition;
import dev.postmark.model.StampIdentity;
import dev.postmark.render.StampArtwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;
import java.lang.reflect.Method;
import java.util.*;

public final class SignMeUpBridge {
    private static final String ROOT = "org.teacon.exhibition_portal.";
    private static final dev.postmark.model.StampCaptureCache SNAPSHOTS = new dev.postmark.model.StampCaptureCache();
    private static final Map<String,String> LABELS = new HashMap<>();
    private static Object lastGallery;
    private static Pending pending;
    private static String activeScope = "";
    private static boolean openPostcard;
    private static int ticks;
    private static String notice = "";
    private record Pending(UUID exhibition, String id, String item, Object baseline,
                           MapPlacement.Rect rectangle, int deadline, String scope) {}
    private SignMeUpBridge() {}
    public static String notice() { return notice; }
    public static String requestedKey() { return pending == null ? null : StampIdentity.key(pending.exhibition,pending.id,pending.item); }
    public static void capture(Object exhibition, Object stamp) {
        openPostcard = false;
        try {
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.getConnection() == null) { originalEdit(exhibition,stamp); return; }
            ClientSession.get();
            resetScope();
            UUID uuid = (UUID)call(exhibition,"uuid");
            String id = (String)call(stamp,"id"), item = call(stamp,"item").toString();
            MapPlacement.Rect rectangle = null;
            try { rectangle = position(); }
            catch (Exception e) {
                notice = "地图定位不可用，保留原印迹：" + rootMessage(e);
                Postmark.LOGGER.warn("Cannot position automatic map stamp",e);
            }
            // Wait for a fresh server snapshot after the interaction before sending the update.
            pending = new Pending(uuid,id,item,gallery(),rectangle,ticks+200,ClientSession.scope());
            openPostcard = true;
            if (rectangle != null) notice = "正在等待盖章台确认…";
        } catch (Exception e) {
            Postmark.LOGGER.error("Postmark could not open; using the original stamp screen",e);
            notice = "明信片打开失败，已回到原盖章界面";
            originalEdit(exhibition,stamp);
        }
    }
    public static void open(Minecraft mc, Screen original) {
        if (openPostcard) {
            openPostcard = false;
            PostcardScreen.show(null, requestedKey());
        } else mc.setScreen(original);
    }
    public static void tick() {
        ticks++;
        if (!ModList.get().isLoaded("exhibition_portal")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getConnection() == null) {
            pending = null; lastGallery = null; SNAPSHOTS.clear(); LABELS.clear(); activeScope = ""; return;
        }
        if (pending == null && ticks % 20 != 0) return;
        try {
            resetScope();
            Map<?,?> lookup = gallery();
            if (lookup != lastGallery && !lookup.isEmpty()) {
                // Read the complete snapshot before mutating anything. An initial empty lookup is not a reset.
                var venues=new HashSet<String>();var owned=new HashSet<String>();
                for(Object exhibition:lookup.values()) {
                    String venue=((UUID)call(exhibition,"uuid")).toString();venues.add(venue);
                    for(Object stamp:stamps(exhibition))owned.add(venue+"/"+call(stamp,"id"));
                }
                var session=ClientSession.get();var next=session.album().reconcileStamps(venues,owned);
                if(next!=session.album())session.update(next);
                SNAPSHOTS.retainSlots(owned);LABELS.keySet().removeIf(key->!owned.contains(StampIdentity.slot(key)));
                for(Object exhibition:lookup.values())for(Object stamp:stamps(exhibition))snapshot(exhibition,stamp);
                lastGallery=lookup;
            }
            if (pending != null) {
                Pending p = pending;
                if (ticks > p.deadline) { notice = "盖章台确认超时，请再次点击盖章台"; pending = null; }
                else if (lookup != p.baseline) {
                    Object exhibition = lookup.get(p.exhibition);
                    if (exhibition != null) {
                        for (Object stamp : stamps(exhibition)) {
                            if (p.id.equals(call(stamp,"id")) && p.item.equals(call(stamp,"item").toString())) {
                                if (p.rectangle != null) sendMapStamp(p);
                                snapshot(exhibition,stamp);
                                notice = p.rectangle == null ? "印章已收集；地图位置不可用，保留原印迹" : "地图已按当前位置盖章 · 明信片可以自由盖印";
                                pending = null; break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (!notice.startsWith("印章兼容失败")) Postmark.LOGGER.warn("SignMeUp compatibility failed",e);
            notice = "印章兼容失败："+rootMessage(e);
            pending = null;
        }
    }
    private static void resetScope() {
        String scope = ClientSession.scope();
        if (!scope.equals(activeScope)) {
            activeScope = scope; SNAPSHOTS.clear(); LABELS.clear(); lastGallery = null; pending = null;
        }
    }
    private static void snapshot(Object exhibition, Object stamp) throws Exception {
        String item = call(stamp,"item").toString();
        String key = StampIdentity.key((UUID)call(exhibition,"uuid"),call(stamp,"id").toString(),item);
        var session=ClientSession.get();
        String id=call(stamp,"id").toString();
        String title=call(call(exhibition,"metadata"),"name").toString().strip();
        String kind=id.equals("expert")?"大师章":id.equals("visitor")?"普通章":id;
        String label=(title.isEmpty() || title.equals("@unset"))?"未命名展区 · "+kind:title+" · "+kind;
        LABELS.put(key,label);
        var cached=SNAPSHOTS.get(key);
        if(cached!=null && item.equals(cached.item())) {
            var existing=session.album().stamps().stream().filter(s->s.key().equals(key)).findFirst();
            if(existing.isPresent() && !existing.get().name().equals(label))
                session.update(session.album().unlock(new StampDefinition(key,label,existing.get().asset(),false)));
            return;
        }
        String scope=ClientSession.scope();
        var connection=Minecraft.getInstance().getConnection();
        var ticket=SNAPSHOTS.start(key,item);
        StampArtwork.capture(item).whenComplete((image,error)->Minecraft.getInstance().execute(()->{
            try {
                if(!scope.equals(ClientSession.scope()) || connection!=Minecraft.getInstance().getConnection()
                        || session!=ClientSession.get() || !SNAPSHOTS.current(key,ticket)) return;
                if(error!=null) throw new java.io.IOException("Cannot render stamp "+item,error);
                String asset=session.store().putImage(image);
                session.update(session.album().unlockCaptured(new StampDefinition(key,LABELS.getOrDefault(key,label),asset,false)));
            } catch(Exception e) {
                SNAPSHOTS.failed(key,ticket);
                Postmark.LOGGER.warn("Cannot snapshot stamp {} ({})",key,item,e);
                notice="章面读取失败，未使用替代图案："+item;
            }
        }));
    }
    @SuppressWarnings("unchecked")
    static Map<?,?> gallery() throws Exception {
        Object parameter = Class.forName(ROOT+"client.EPClient").getField("GALLERY_LOOKUP").get(null);
        return (Map<?,?>)readLayout(parameter);
    }
    private static Object readLayout(Object parameter) throws Exception {
        Class<?> access = Class.forName(ROOT+"client.framework.binding.RenderAccess");
        for (Method m : access.getMethods()) {
            if (m.getName().equals("get") && m.getParameterCount()==1 && m.getParameterTypes()[0].isInstance(parameter))
                return m.invoke(null,parameter);
        }
        throw new NoSuchMethodException("RenderAccess.get");
    }
    private static List<?> stamps(Object exhibition) throws Exception { return (List<?>)call(call(exhibition,"footprint"),"stamps"); }
    static Object call(Object obj, String method) throws Exception { return obj.getClass().getMethod(method).invoke(obj); }
    private static MapPlacement.Rect position() throws Exception {
        var mc = Minecraft.getInstance();
        try (var reader = mc.getResourceManager().openAsReader(Identifier.parse("exhibition_portal:textures/gui/map.json"))) {
            var config=JsonParser.parseReader(reader).getAsJsonObject();
            var full=config.getAsJsonObject("full");
            double x0=full.get("x0").getAsDouble(), z0=full.get("y0").getAsDouble();
            double worldWidth=full.get("x1").getAsDouble()-x0, worldHeight=full.get("y1").getAsDouble()-z0;
            // SMU's MAP_AREA is scaled from COORDINATES_FULL, not the PNG's pixel dimensions.
            double displayAspect=Math.abs(worldWidth/worldHeight);
            Object area=readLayout(Class.forName(ROOT+"client.screens.MapLayouts").getField("MAP_AREA").get(null));
            double mapWidth=((Number)call(area,"w")).doubleValue();
            if(!(mapWidth>0)) {
                // SMU fits the essential region into its map panel on the first opening.
                var essential=config.getAsJsonObject("essential");
                double ew=Math.abs(essential.get("x1").getAsDouble()-essential.get("x0").getAsDouble());
                double eh=Math.abs(essential.get("y1").getAsDouble()-essential.get("y0").getAsDouble());
                if(ew<=0 || eh<=0) throw new IllegalArgumentException("Invalid essential map bounds");
                double k=Math.min(mc.getWindow().getGuiScaledWidth()*.8/ew,mc.getWindow().getGuiScaledHeight()/eh);
                mapWidth=Math.abs(worldWidth)*k;
            }
            double visibleFraction=.59375; // Stock me_map.png: longest visible extent is 19 / 32.
            try(var iconStream=mc.getResourceManager().open(Identifier.parse("exhibition_portal:textures/gui/me_map.png"))) {
                var icon=javax.imageio.ImageIO.read(iconStream);
                if(icon!=null) {
                    int minX=icon.getWidth(),minY=icon.getHeight(),maxX=-1,maxY=-1;
                    for(int iy=0;iy<icon.getHeight();iy++) for(int ix=0;ix<icon.getWidth();ix++) if((icon.getRGB(ix,iy)>>>24)>=32) {
                        minX=Math.min(minX,ix);maxX=Math.max(maxX,ix);minY=Math.min(minY,iy);maxY=Math.max(maxY,iy);
                    }
                    if(maxX>=minX) visibleFraction=Math.max((double)(maxX-minX+1)/icon.getWidth(),(double)(maxY-minY+1)/icon.getHeight());
                }
            }
            double size=MapPlacement.playerSizedRatio(mapWidth,visibleFraction);
            return MapPlacement.at(mc.player.getX(),mc.player.getZ(),x0,z0,worldWidth,worldHeight,displayAspect,size);
        }
    }
    private static void sendMapStamp(Pending p) throws Exception {
        if (!p.scope.equals(ClientSession.scope())) return;
        Class<?> rectangle = Class.forName(ROOT+"client.framework.components.Rectangle");
        var r=p.rectangle;
        Object location=rectangle.getConstructor(float.class,float.class,float.class,float.class).newInstance(r.x(),r.y(),r.width(),r.height());
        Class<?> stampClass = Class.forName(ROOT+"components.ExhibitionStamp");
        Object stamp=stampClass.getConstructor(String.class,Identifier.class,rectangle,float.class).newInstance(p.id,Identifier.parse(p.item),location,0f);
        var packet=(CustomPacketPayload)Class.forName(ROOT+"network.UpdateExhibitionStampPacket").getConstructor(UUID.class,stampClass).newInstance(p.exhibition,stamp);
        Minecraft.getInstance().getConnection().send(packet);
    }
    private static void originalEdit(Object exhibition,Object stamp) {
        try {
            Class.forName(ROOT+"client.screens.stamp.StampScreenLayouts")
                    .getMethod("setEditingExhibition",exhibition.getClass(),stamp.getClass()).invoke(null,exhibition,stamp);
        } catch (Exception e) { throw new IllegalStateException("Cannot restore SignMeUp editor",e); }
    }
    private static String rootMessage(Throwable error) {
        while(error.getCause()!=null) error=error.getCause();
        return error.getMessage()==null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
