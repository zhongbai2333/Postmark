package dev.postmark.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

public final class StampArtwork {
    private StampArtwork() {}
    public static CompletableFuture<BufferedImage> capture(String source) {
        try {
            Identifier id=Identifier.parse(source);
            var item=BuiltInRegistries.ITEM.get(id);
            // Mirror SMU's decision exactly: registered item first, otherwise the raw texture.
            if(item.isPresent()) return ItemStampCapture.request(new ItemStack(item.get()));
            try(var input=Minecraft.getInstance().getResourceManager().open(id)) {
                BufferedImage image=ImageIO.read(input);
                if(image==null) throw new IOException("Unreadable stamp texture: "+source);
                return CompletableFuture.completedFuture(image);
            }
        } catch(Exception error) { return CompletableFuture.failedFuture(error); }
    }
}