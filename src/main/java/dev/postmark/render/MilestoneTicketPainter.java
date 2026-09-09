package dev.postmark.render;

import java.awt.*;
import java.awt.image.BufferedImage;

/** An earned paper keepsake, deliberately separate from the SMU stamp collection. */
public final class MilestoneTicketPainter {
    private MilestoneTicketPainter() {}
    public static BufferedImage paint(int milestone) {
        if(milestone<10 || milestone%10!=0 || milestone>4090) throw new IllegalArgumentException("Invalid milestone");
        BufferedImage image=new BufferedImage(160,160,BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();
        try {
            g.setColor(new Color(0xB5A080));g.fillRect(5,48,150,70);
            g.setColor(new Color(0xF2E6CA));g.fillRect(3,44,150,70);
            g.setColor(new Color(0xFFF5DF));g.fillRect(5,46,146,2);
            g.setColor(new Color(0xD0BD96));g.drawRect(10,51,135,55);
            for(int y=48;y<114;y+=8) { g.setComposite(AlphaComposite.Clear);g.fillRect(3,y,4,4);g.fillRect(149,y,4,4);g.setComposite(AlphaComposite.SrcOver); }
            g.setColor(new Color(0x587452));PixelFont.draw(g,"POSTMARK",18,58,1);
            String count=Integer.toString(milestone);PixelFont.draw(g,count,81-count.length()*6,74,2);
            g.setColor(new Color(0xA89267));
            for(int x=20;x<141;x+=12) g.fillRect(x,99,4,2);
            g.fillRect(126,59,10,2);g.fillRect(128,56,6,8);
        } finally { g.dispose(); }
        return image;
    }
}
