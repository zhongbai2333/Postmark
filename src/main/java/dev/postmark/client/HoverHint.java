package dev.postmark.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** One consistent bottom line for contextual instructions. */
public final class HoverHint {
    private HoverHint() {}
    public static void draw(GuiGraphicsExtractor g,Font font,String text,int width,int height) {
        if(text==null || text.isBlank()) return;
        if(font.width(text)>width-32) text=font.plainSubstrByWidth(text,width-44)+"…";
        g.centeredText(font,Component.literal(text),width/2,height-16,0xFFECE8D7);
    }
}
