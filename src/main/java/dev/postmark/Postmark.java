package dev.postmark;

import dev.postmark.client.PostcardScreen;
import dev.postmark.compat.SignMeUpBridge;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value="postmark", dist=Dist.CLIENT)
public final class Postmark {
    public static final Logger LOGGER = LoggerFactory.getLogger("Postmark");
    private KeyMapping open;
    public Postmark(IEventBus bus) {
        bus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(dev.postmark.compat.PostmarkCommands::register);
        NeoForge.EVENT_BUS.addListener(dev.postmark.client.GuideEntry::initScreen);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH,dev.postmark.client.GuideEntry::screenMousePressed);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH,dev.postmark.client.GuideEntry::screenMouseDragged);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH,dev.postmark.client.GuideEntry::screenMouseReleased);
        NeoForge.EVENT_BUS.addListener(dev.postmark.compat.GuideBridge::chunkLoaded);
        NeoForge.EVENT_BUS.addListener(dev.postmark.compat.GuideBridge::chunkUnloaded);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) -> {dev.postmark.render.ItemStampCapture.afterFrame();dev.postmark.render.NativeTextCapture.afterFrame();});
    }
    private void registerKeys(RegisterKeyMappingsEvent event) {
        open=new KeyMapping("key.postmark.open",GLFW.GLFW_KEY_P,KeyMapping.Category.MISC);
        event.register(open);
    }
    private void tick(ClientTickEvent.Post event) {
        SignMeUpBridge.tick();
        dev.postmark.compat.GuideBridge.tick();
        var mc=Minecraft.getInstance();
        if(open!=null) while(open.consumeClick()) {
            if(mc.screen==null && mc.level!=null) PostcardScreen.show(null,null);
        }
    }
}
