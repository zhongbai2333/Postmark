package dev.postmark.render;

import dev.postmark.model.StampDefinition;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Composite the entire tool once, so transparency is applied to the object rather than each wood layer. */
public final class StampToolPainter {
    public static final int WIDTH=80,HEIGHT=104,ORIGIN_X=40,ORIGIN_Y=64;
    private StampToolPainter() {}
    public static BufferedImage paint(StampDefinition stamp,BufferedImage art) {
        var image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics(); PostcardPainter.pixel(g);
        try {
        int x=40,y=64,size=64;
        int left=x-size/2,bottom=y+size/2;
        fill(g,left+4,bottom-size+8,left+size+5,bottom+7,0x24322C21);
        fill(g,left+1,bottom-size+5,left+size+2,bottom+4,0x34322C21);
        // Stepped wood base, brass collar, and dark rubber lip retain a crisp pixel silhouette.
        fill(g,left-3,bottom-size+2,left+size+3,bottom-2,0xFF503D2E);
        fill(g,left-1,bottom-size,left+size+1,bottom,0xFF503D2E);
        fill(g,left-1,bottom-size+2,left+size+1,bottom-6,0xFFAD8053);
        fill(g,left+1,bottom-size+3,left+size-1,bottom-size+6,0xFFD3AB75);
        fill(g,left+size-3,bottom-size+6,left+size+1,bottom-6,0xFF88613F);
        fill(g,left,bottom-6,left+size,bottom-3,0xFFC0A271);
        fill(g,left+1,bottom-3,left+size-1,bottom+1,0xFF343A32);
        fill(g,left+7,bottom-size+7,left+size-7,bottom-9,0xFF684F36);
        fill(g,left+9,bottom-size+9,left+size-9,bottom-11,0xFFE2CDA2);
        fill(g,left+10,bottom-size+10,left+size-10,bottom-size+12,0xFFF6E5BC);
        {
            int side=40;
            double fit=(double)side/Math.max(art.getWidth(),art.getHeight());
            int aw=Math.max(1,(int)Math.round(art.getWidth()*fit)),ah=Math.max(1,(int)Math.round(art.getHeight()*fit));
            g.drawImage(art,x-aw/2,bottom-size/2-1-ah/2,aw,ah,null);
        }
        int hw=Math.max(10,size/3),top=bottom-size-26,cx=(int)x;
        boolean expert=stamp.expert();
        int rim=expert?0xFF806021:0xFF533C2D;
        int stem=expert?0xFFAF822B:0xFF62442E;
        int body=expert?0xFFD6AA46:0xFF9C6946;
        int light=expert?0xFFFFE49A:0xFFC39361;
        int edge=expert?0xFFF0CB69:0xFFB98455;
        fill(g,cx-hw/2-3,bottom-size-3,cx+hw/2+3,bottom-size+5,expert?0xFF8D6A26:0xFF78603D);
        fill(g,cx-hw/2-2,bottom-size-3,cx+hw/2+2,bottom-size,expert?0xFFFFD879:0xFFD4BB82);
        fill(g,cx-hw/2+2,top+10,cx+hw/2-2,bottom-size-3,stem);
        fill(g,cx-hw/2+3,top+10,cx-hw/2+6,bottom-size-4,expert?0xFFE8C264:0xFFA97A4F);
        fill(g,cx-hw/2-4,top+4,cx+hw/2+4,top+14,rim);
        fill(g,cx-hw/2-2,top+1,cx+hw/2+2,top+17,rim);
        fill(g,cx-hw/2-2,top+4,cx+hw/2+1,top+12,body);
        fill(g,cx-hw/2,top+2,cx+hw/2,top+5,light);
        fill(g,cx-hw/2,top+5,cx-hw/2+3,top+11,edge);
        if(expert) {
            // A small inset gives the metal head a highlight visible even at pouch scale.
            fill(g,cx+2,top+6,cx+6,top+10,0xFFB18126);
            fill(g,cx+2,top+6,cx+4,top+8,0xFFFFEDB2);
            fill(g,cx-hw/2+2,top+12,cx+hw/2-1,top+14,0xFFBA8B30);
        }
        } finally { g.dispose(); }
        return image;
    }
    private static void fill(Graphics2D g,int x0,int y0,int x1,int y1,int color) {
        g.setColor(new Color(color,true));g.fillRect(x0,y0,x1-x0,y1-y0);
    }
}
