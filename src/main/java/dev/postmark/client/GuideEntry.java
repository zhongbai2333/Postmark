package dev.postmark.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** A folded paper publication, shared by the desk and inventory mouse entries. */
public final class GuideEntry extends AbstractWidget {
    private final Screen parent;
    private GuideEntry(Screen parent) {super(0,0,54,40,Component.literal("打开漫游志"));this.parent=parent;position();}
    private void position() {
        var inventory=(AbstractContainerScreen<?>)parent;
        setX(Math.min(parent.width-58,inventory.getGuiLeft()+inventory.getXSize()+8));
        setY(Math.clamp(inventory.getGuiTop()+10,8,Math.max(8,parent.height-44)));
    }
    public static int deskX(int width) {return Math.min(108,width-34);}
    public static int deskY(int height) {return (int)(height*.1)+36;}
    public static boolean hit(double mx,double my,int width,int height) {return Math.abs(mx-deskX(width))<=27 && Math.abs(my-deskY(height))<=20;}
    public static void initScreen(ScreenEvent.Init.Post event) {
        var screen=event.getScreen();
        if(screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)
            event.addListener(new GuideEntry(screen));
    }
    public static void draw(GuiGraphicsExtractor g,int x,int y,boolean hover) {
        g.pose().pushMatrix();g.pose().rotateAbout(-.10f,x,y);if(hover)g.pose().translate(0,-3);
        g.fill(x-21,y-13,x+25,y+19,0x34352F20);g.fill(x-24,y-17,x+22,y+15,0xFFC9BB94);
        g.fill(x-24,y-19,x+21,y+12,0xFFF0E5C9);g.fill(x-24,y-19,x+21,y-17,0xFFFFF4D8);
        var font=Minecraft.getInstance().font;
        g.centeredText(font,Component.literal("漫游志"),x-1,y-14,0xFF526548);
        g.fill(x-1,y-1,x,y+11,0xFFD2C4A0);
        g.pose().pushMatrix();g.pose().rotateAbout(-.15f,x-12,y+3);
        g.fill(x-20,y-4,x-5,y+11,0xFFFCF0CF);g.fill(x-18,y-2,x-7,y+7,0xFF879870);
        g.fill(x-15,y-2,x-12,y+4,0xFFB1BD84);g.fill(x-18,y-6,x-9,y-3,0xCCB4B58D);g.pose().popMatrix();
        g.pose().pushMatrix();g.pose().rotateAbout(.14f,x+9,y+4);
        g.fill(x+1,y-1,x+17,y+12,0xFFF9ECCD);g.fill(x+3,y+1,x+15,y+8,0xFF9EAFAD);
        g.fill(x+5,y+5,x+12,y+8,0xFF6C8371);g.fill(x+7,y-3,x+16,y,0xCCB4B58D);g.pose().popMatrix();
        g.pose().popMatrix();
    }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float t) {
        position();
        draw(g,getX()+27,getY()+20,isHoveredOrFocused());
        if(isHoveredOrFocused())HoverHint.draw(g,Minecraft.getInstance().font,"展开漫游志",parent.width,parent.height);
    }
    @Override public void onClick(MouseButtonEvent event,boolean twice) {TravelGuideScreen.show(parent);}
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {defaultButtonNarrationText(output);}
}
