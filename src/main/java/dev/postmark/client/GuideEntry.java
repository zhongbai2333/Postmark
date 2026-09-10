package dev.postmark.client;

import dev.postmark.Postmark;
import dev.postmark.model.GuideEntryPosition;
import dev.postmark.storage.GuideEntryPositionStore;
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
    private final GuideEntryPositionStore positions=new GuideEntryPositionStore(Minecraft.getInstance().gameDirectory.toPath());
    private boolean pressed,dragging;
    private double pressX,pressY;
    private int originX,originY;
    private String error="";
    private GuideEntry(Screen parent) {super(0,0,54,40,Component.literal("漫游志：点击打开，拖动调整位置"));this.parent=parent;position();}
    private void position() {
        var inventory=(AbstractContainerScreen<?>)parent;
        setX(Math.min(parent.width-58,inventory.getGuiLeft()+inventory.getXSize()+8));
        setY(Math.clamp(inventory.getGuiTop()+10,8,Math.max(8,parent.height-44)));
        try {var saved=positions.load();place(saved==null?relative(getX(),getY()):saved);}
        catch(java.io.IOException e) {place(relative(getX(),getY()));Postmark.LOGGER.warn("Cannot load guide entry position",e);}
    }
    private GuideEntryPosition relative(int x,int y) {return GuideEntryPosition.at(x,y,parent.width,parent.height,getWidth(),getHeight());}
    private void place(GuideEntryPosition position) {setX(position.x(parent.width,getWidth()));setY(position.y(parent.height,getHeight()));}
    private static GuideEntry entry(Screen screen) {return screen.children().stream().filter(GuideEntry.class::isInstance).map(GuideEntry.class::cast).findFirst().orElse(null);}
    // Consume the entire gesture before container slots handle it, including releases outside the entry.
    public static void screenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        var entry=entry(event.getScreen());
        if(entry!=null && entry.mouseClicked(event.getMouseButtonEvent(),event.isDoubleClick()))event.setCanceled(true);
    }
    public static void screenMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        var entry=entry(event.getScreen());
        if(entry!=null && entry.mouseDragged(event.getMouseButtonEvent(),event.getDragX(),event.getDragY()))event.setCanceled(true);
    }
    public static void screenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        var entry=entry(event.getScreen());
        if(entry!=null && entry.mouseReleased(event.getMouseButtonEvent()))event.setCanceled(true);
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
        draw(g,getX()+27,getY()+20,isHoveredOrFocused()||dragging);
        if(isHoveredOrFocused()||pressed)HoverHint.draw(g,Minecraft.getInstance().font,error.isEmpty()?(dragging?"松开放在这里":"点击打开 · 拖动挪位置"):error,parent.width,parent.height);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice) {
        if(event.button()!=0 || !isMouseOver(event.x(),event.y()))return false;
        pressed=true;dragging=false;error="";pressX=event.x();pressY=event.y();originX=getX();originY=getY();return true;
    }
    private void move(MouseButtonEvent event) {
        double dx=event.x()-pressX,dy=event.y()-pressY;
        if(dx*dx+dy*dy>=16)dragging=true;
        if(dragging)place(relative(originX+(int)Math.round(dx),originY+(int)Math.round(dy)));
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
        if(!pressed || event.button()!=0)return false;
        move(event);return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if(!pressed || event.button()!=0)return false;
        move(event);pressed=false;
        if(dragging) {
            dragging=false;
            try {positions.save(relative(getX(),getY()));}
            catch(java.io.IOException e) {error="位置未能保存，请再拖动一次";Postmark.LOGGER.warn("Cannot save guide entry position",e);}
        } else if(isMouseOver(event.x(),event.y())) {playDownSound(Minecraft.getInstance().getSoundManager());TravelGuideScreen.show(parent);}
        return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {defaultButtonNarrationText(output);}
}
