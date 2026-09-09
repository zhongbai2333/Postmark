package dev.postmark.render;

import dev.postmark.model.Postcard;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;

/** One composition for the on-screen preview and exported PNG. */
public final class PostcardPainter {
    public static final int WIDTH=1800, HEIGHT=1200;
    @FunctionalInterface public interface Images { BufferedImage get(String name) throws IOException; }
    private PostcardPainter() {}
    public static BufferedImage paint(Postcard card,Images images) throws IOException {
        int width=card.width(),height=card.height();
        var canvas=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=canvas.createGraphics(); pixel(g);
        try {
            g.drawImage(paper(false,width,height),0,0,width,height,null);
            if(card.background()!=null) {
                BufferedImage photo=images.get(card.background());
                double scale=Math.max((double)width/photo.getWidth(),(double)height/photo.getHeight());
                int w=(int)Math.ceil(photo.getWidth()*scale),h=(int)Math.ceil(photo.getHeight()*scale);
                g.drawImage(photo,(width-w)/2,(height-h)/2,w,h,null);
            }
            for(var stamp:card.imprints()) {
                BufferedImage ink=images.get(stamp.asset());
                AffineTransform before=g.getTransform();
                double size=stamp.size()*width;
                g.translate(stamp.x()*width,stamp.y()*height); g.rotate(stamp.angle());
                // Preserve the original item/texture appearance. No added ring, recoloring or ink mask.
                g.drawImage(ink,(int)(-size/2),(int)(-size/2),(int)size,(int)size,null);
                g.setTransform(before);
            }
        } finally { g.dispose(); }
        return canvas;
    }
    private static BufferedImage watermark;
    private static synchronized BufferedImage watermark() throws IOException {
        if(watermark==null) {
            try(var in=PostcardPainter.class.getResourceAsStream("/assets/postmark/textures/paper/teacon.png")) {
                if(in==null) throw new IOException("Missing TeaCon watermark artwork");
                watermark=javax.imageio.ImageIO.read(in);
                if(watermark==null) throw new IOException("Unreadable TeaCon watermark artwork");
            }
        }
        return watermark;
    }
    private static BufferedImage paper(boolean back) throws IOException { return paper(back,WIDTH,HEIGHT); }
    private static BufferedImage paper(boolean back,int width,int height) throws IOException {
        int w=Math.max(1,width/6),h=Math.max(1,height/6),unit=Math.min(w,h);
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();pixel(g);
        try {
            g.setColor(new Color(back?0xE8DABD:0xF4F0E3));g.fillRect(0,0,w,h);
            g.setColor(new Color(0xDFD8C5));g.fillRect(0,h-2,w,2);g.fillRect(w-2,0,2,h);
            g.setColor(new Color(0xFCF9F0));g.fillRect(0,0,w-2,1);g.fillRect(0,1,1,h-3);
            int inset=Math.min(7,unit/8);
            g.setColor(new Color(0xD9D2C0));g.drawRect(inset,inset,w-inset*2-2,h-inset*2-2);
            int logo=(int)(unit*.73);
            g.setComposite(AlphaComposite.SrcOver.derive(back?.035f:.065f));
            g.drawImage(watermark(),(w-logo)/2,(h-logo)/2,logo,logo,null);
            g.setComposite(AlphaComposite.SrcOver);
            if(back) {
                g.setColor(new Color(0xADAA9A));
                int pad=Math.min(19,Math.max(2,unit/10));
                int labelScale=Math.max(1,Math.min(w/180,h/120));
                if(w>=80) PixelFont.draw(g,"POSTMARK",pad,pad,labelScale);
                int markW=Math.max(2,(int)(unit*.09)),markH=Math.max(3,(int)(unit*.12));
                g.drawRect(w-pad-markW,pad,markW,markH);
                g.fillRect(w-pad-markW+markW/4,pad+markH/4,Math.max(1,markW/2),Math.max(1,markH*3/5));
                int sx=Math.min((int)(w*.58),Math.max(pad,w-pad-64)),sy=h-Math.max(20,h/10);
                if(w>=90 && h>=50) PixelFont.draw(g,"SIGNATURE",sx,sy-15,1);
                g.fillRect(sx,sy,Math.max(1,w-pad-sx),1);
            }
        } finally {g.dispose();}
        return image;
    }
    public static BufferedImage paintEnvelopeFront() { return paintEnvelopeFront(Postcard.blank()); }
    public static BufferedImage paintEnvelopeFront(Postcard card) {
        int w=Math.max(1,card.width()/6),h=Math.max(1,card.height()/6);
        var small=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        var g=small.createGraphics();pixel(g);
        try {
            g.setColor(new Color(0xE6D5B4));g.fillRect(0,0,w,h);
            g.setColor(new Color(0xF0E2C4));g.fillPolygon(new int[]{0,w/2,w},new int[]{h,h/3,h},3);
            g.setColor(new Color(0xC6B590));g.drawLine(0,h-1,w/2,h/3);g.drawLine(w/2,h/3,w-1,h-1);
            int tip=h*2/3;
            g.setColor(new Color(0xC4AF89));g.fillPolygon(new int[]{0,w,w/2},new int[]{1,1,tip+3},3);
            g.setColor(new Color(0xEADABD));g.fillPolygon(new int[]{0,w,w/2},new int[]{0,0,tip},3);
            int seal=Math.max(1,Math.min(11,Math.min(w,h)/12)),cx=w/2,cy=tip;
            g.setColor(new Color(0x853F32));g.fillRect(cx-seal,cy-seal*2/3,seal*2,seal*4/3);g.fillRect(cx-seal*2/3,cy-seal,seal*4/3,seal*2);
            g.setColor(new Color(0xAD5941));g.fillRect(cx-seal+2,cy-seal+2,Math.max(1,seal*2-4),Math.max(1,seal*2-4));
            if(seal>=5) {g.setColor(new Color(0xF1C994));PixelFont.draw(g,"P",cx-2,cy-3,1);}
        } finally {g.dispose();}
        var image=new BufferedImage(card.width(),card.height(),BufferedImage.TYPE_INT_ARGB);
        var out=image.createGraphics();pixel(out);out.drawImage(small,0,0,card.width(),card.height(),null);out.dispose();return image;
    }
    public static BufferedImage paintBack(Postcard card) throws IOException {
        int width=card.width(),height=card.height();
        var canvas=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=canvas.createGraphics(); pixel(g);
        try {
            g.drawImage(paper(true,width,height),0,0,width,height,null);
            for(var stroke:card.signature()) {
                g.setColor(new Color(stroke.color(),true));
                float weight=(float)(stroke.width()*width);
                g.setStroke(new BasicStroke(weight,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                var first=stroke.points().getFirst();
                if(stroke.points().size()==1) {
                    g.fill(new java.awt.geom.Ellipse2D.Double(first.x()*width-weight/2,first.y()*height-weight/2,weight,weight));
                } else {
                    var path=new java.awt.geom.Path2D.Double(); path.moveTo(first.x()*width,first.y()*height);
                    for(var point:stroke.points()) path.lineTo(point.x()*width,point.y()*height);
                    g.draw(path);
                }
            }
        } finally { g.dispose(); }
        return canvas;
    }
    public static BufferedImage practice(int variant) {
        var image=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics(); pixel(g);
        try {
            if(variant==0) {
                g.setColor(new Color(0x65452D)); g.fillRect(9,3,14,2); g.fillRect(5,7,22,18); g.fillRect(9,27,14,2);
                g.fillRect(7,5,18,22); g.setColor(new Color(0xC49B55)); g.fillRect(9,7,14,18); g.fillRect(7,9,18,14);
                g.setColor(new Color(0xEFE1B5)); g.fillRect(10,9,12,14);
                g.setColor(new Color(0xB74C36)); g.fillRect(15,8,2,10); g.fillRect(13,12,6,5);
                g.setColor(new Color(0x4B7974)); g.fillRect(15,18,2,7); g.fillRect(13,17,6,3);
                g.setColor(new Color(0x3B4436)); g.fillRect(14,15,4,3);
            } else if(variant==1) {
                g.setColor(new Color(0x365343));
                for(int y=5;y<26;y+=3) g.fillRect(15-(y-5)/2,y,3+y-5,3);
                g.setColor(new Color(0x83A17A));
                for(int y=11;y<26;y+=3) g.fillRect(16,y,1+(y-8)/2,3);
                g.setColor(new Color(0xF0EBD6)); g.fillRect(14,5,4,3); g.fillRect(12,8,8,3); g.fillRect(12,11,3,2);
                g.setColor(new Color(0x6D8060)); g.fillRect(3,26,26,2);
            } else {
                g.setColor(new Color(0x37596B)); g.fillRect(2,7,28,20);
                g.setColor(new Color(0xF0E4C1)); g.fillRect(4,9,24,16);
                g.setColor(new Color(0x839BA0));
                for(int i=0;i<6;i++) { g.fillRect(4+i*2,9+i*2,3,2); g.fillRect(25-i*2,9+i*2,3,2); }
                g.setColor(new Color(0xB7563C)); g.fillRect(23,11,3,4);
            }
        } finally { g.dispose(); }
        return image;
    }
    public static void pixel(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_SPEED);
    }
}