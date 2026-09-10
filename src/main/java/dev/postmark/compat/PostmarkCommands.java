package dev.postmark.compat;

import dev.postmark.Postmark;
import dev.postmark.client.ClientSession;
import dev.postmark.model.*;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import java.util.*;
import static dev.postmark.compat.SignMeUpBridge.call;

public final class PostmarkCommands {
    private PostmarkCommands() {}
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("postmark").then(Commands.literal("export").executes(context->{
            try {
                var data=collect();var path=data.write(ClientSession.get().store().directory().resolve("exports"));
                context.getSource().sendSuccess(()->Component.literal("已导出 "+data.venues().size()+" 个展馆、"+data.ownedCount()+" 枚服务器已领取章。"),false);
                context.getSource().sendSuccess(()->Component.literal("[打开导出文件夹] "+path.getFileName()).withStyle(style->style.withColor(ChatFormatting.GREEN).withUnderlined(true).withClickEvent(new ClickEvent.OpenFile(path.getParent()))),false);
                return 1;
            }catch(Exception e) {
                Postmark.LOGGER.warn("Cannot export stamp catalog",e);
                context.getSource().sendFailure(Component.literal("章清单导出失败："+e.getMessage()));return 0;
            }
        })));
    }
    public static StampCatalogExport collect() throws Exception {
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null || !ModList.get().isLoaded("exhibition_portal"))throw new IllegalStateException("请进入已加载 SMU 的世界后重试");
        var gallery=SignMeUpBridge.gallery();
        if(gallery.isEmpty())throw new IllegalStateException("尚未收到 SMU 展馆数据，请稍后重试");
        var session=ClientSession.get();var journal=session.journal();var result=new ArrayList<StampCatalogExport.Venue>();
        var venues=GuideBridge.venues();
        for(Object exhibition:gallery.values()) {
            UUID id=(UUID)call(exhibition,"uuid");String name=call(call(exhibition,"metadata"),"name").toString();
            var stamps=new TreeMap<String,StampCatalogExport.Stamp>();
            for(var found:journal.entry(id).stamps())stamps.put(found.id(),new StampCatalogExport.Stamp(found.id(),found.item(),"local_discovered"));
            for(Object owned:(List<?>)call(call(exhibition,"footprint"),"stamps")) {
                String key=(String)call(owned,"id");stamps.put(key,new StampCatalogExport.Stamp(key,call(owned,"item").toString(),"server_owned"));
            }
            var observed=stamps.values().stream().map(s->new TravelJournal.KnownStamp(s.id(),s.item(),null,s.source().equals("server_owned"))).toList();
            boolean area=venues.stream().filter(v->v.id().equals(id)).anyMatch(journal::areaSearched);
            result.add(new StampCatalogExport.Venue(id,name,journal.entry(id).searched(),area,StampCatalog.bundled().complete(id,observed,area),List.copyOf(stamps.values())));
        }
        result.sort(Comparator.comparing(v->v.uuid().toString()));
        String version=ModList.get().getModContainerById("postmark").orElseThrow().getModInfo().getVersion().toString();
        return StampCatalogExport.create(version,result);
    }
}
