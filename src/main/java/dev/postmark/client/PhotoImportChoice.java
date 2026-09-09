package dev.postmark.client;

import java.awt.image.BufferedImage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;

/** Two mouse-selectable compositions; explanatory text stays in the bottom hover line. */
public record PhotoImportChoice(BufferedImage photo,Identifier cropped,Identifier original,double ratio) {
    private static double tileWidth(int width) { return Math.min(186,(width-56)/2.0); }
    public int hit(double x,double y,int width,int height) {
        if(DeskControls.hit(x,y,width-22,22)) return 2;
        double tw=tileWidth(width),top=height/2.0-100;
        for(int i=0;i<2;i++) {
            double left=width/2.0+(i==0?-tw-10:10);
            if(x>=left && x<=left+tw && y>=top && y<=top+200) return i;
        }
        return -1;
    }
    public void render(GuiGraphicsExtractor g,Font font,int mx,int my,int width,int height) {
        g.fill(0,0,width,height,0xCA30372E);
        int hovered=hit(mx,my,width,height),tw=(int)tileWidth(width),top=height/2-100;
        for(int i=0;i<2;i++) {
            int left=width/2+(i==0?-tw-10:10),cx=left+tw/2;
            g.fill(left+3,top+4,left+tw+3,top+204,0x5030251D);
            g.fill(left,top,left+tw,top+200,hovered==i?0xFFF5EACD:0xFFE1D2B2);
            g.fill(left+2,top+2,left+tw-2,top+4,0xFFFFF4DA);
            double aspect=i==0?1.5:ratio;
            int pw=Math.max(1,(int)Math.min(tw-24,142*aspect)),ph=Math.max(1,(int)(pw/aspect));
            int px=cx-pw/2,py=top+79-ph/2;
            g.blit(i==0?cropped:original,px,py,px+pw,py+ph,0f,1f,0f,1f);
            g.centeredText(font,Component.literal(i==0?"裁切":"原图比例"),cx,top+169,0xFF4E5F45);
        }
        DeskControls.draw(g,DeskControls.Kind.CLOSE,width-22,22,true);
        HoverHint.draw(g,font,switch(hovered) {
            case 0 -> "居中裁切为 3:2 明信片；点击采用左侧预览";
            case 1 -> "完整保留图片，信纸与导出跟随原图比例，章面保持正方形";
            case 2 -> "取消导入，保留当前明信片 · Esc";
            default -> "";
        },width,height);
    }
}
