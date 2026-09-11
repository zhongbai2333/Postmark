package dev.postmark.render;

import java.awt.*;
import java.awt.image.BufferedImage;

/** The guide's paper/photo/caption base only. No stamp, footprint or completion inputs. */
public final class VenueStickerPainter {
    public static final int MARGIN=20;
    private VenueStickerPainter() {}
    public static BufferedImage paint(BufferedImage icon,BufferedImage caption,int size,int variant) {
        if(size<48||size>240)throw new IllegalArgumentException("Invalid venue sticker size");
        int side=size+48,x=MARGIN,y=MARGIN;
        var image=new BufferedImage(side*2,side*2,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();PostcardPainter.pixel(g);g.scale(2,2);
        try {
            int paper=variant==1?0xFFEBDCA9:variant==2?0xFFD6DDC3:0xFFF2E5C7;
            fill(g,x-7,y-5,size+18,size+33,0x383A3021);
            fill(g,x-10,y-8,size+18,size+30,paper);fill(g,x-8,y-10,size+14,2,paper);fill(g,x-8,y+size+22,size+13,2,paper);
            for(int i=0;i<size+12;i+=9)fill(g,x-7+i,y+size+23,4,2,paper);
            fill(g,x-8,y-8,size+14,1,0xFFFFF1D1);fill(g,x-2,y-2,size+4,size+4,0xFFB4A581);
            if(icon!=null)g.drawImage(icon,x,y,size,size,null);
            else {fill(g,x,y,size,size,0xFFD6C69F);g.setColor(new Color(0xB6B395));g.drawRect(x,y,size-1,size-1);}
            int tx=x+size/2-14,ty=y-14,tape=variant%2==0?0xCCBCBA87:0xCCB7C4AC;
            fill(g,tx+1,ty,26,9,tape);fill(g,tx,ty+2,28,5,tape);fill(g,tx+3,ty+1,22,1,0x40FFF6CE);
            for(int i=4;i<26;i+=6)fill(g,tx+i,ty+4,1,4,0x20796845);
            if(variant==0||variant==3)fill(g,x+5,y+size+20,size-11,1,0x8076674B);
            if(caption!=null){g.setTransform(new java.awt.geom.AffineTransform());g.drawImage(caption,0,0,null);}
        }finally{g.dispose();}
        return image;
    }
    private static void fill(Graphics2D g,int x,int y,int w,int h,int color){g.setColor(new Color(color,true));g.fillRect(x,y,w,h);}
}
