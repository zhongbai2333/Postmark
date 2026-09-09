package dev.postmark.render;

import java.awt.image.BufferedImage;

/** Rasterize once in document pixels, shared by the landing ghost and saved composition. */
public final class StampRaster {
    private StampRaster() {}
    public record Layer(int x,int y,BufferedImage image) {}
    public static Layer paint(int width,int height,BufferedImage ink,double x,double y,double size,double angle) {
        double cx=x*width,cy=y*height,pixels=size*width;
        int origin=(int)(-pixels/2),side=(int)pixels;
        double cos=Math.cos(angle),sin=Math.sin(angle),minX=Double.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;
        for(int dx:new int[]{origin,origin+side}) for(int dy:new int[]{origin,origin+side}) {
            double px=cx+dx*cos-dy*sin,py=cy+dx*sin+dy*cos;
            minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);
        }
        int left=Math.max(0,(int)Math.floor(minX)-2),top=Math.max(0,(int)Math.floor(minY)-2);
        int right=Math.min(width,(int)Math.ceil(maxX)+2),bottom=Math.min(height,(int)Math.ceil(maxY)+2);
        var image=new BufferedImage(Math.max(1,right-left),Math.max(1,bottom-top),BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();PostcardPainter.pixel(g);
        try {
            g.translate(cx-left,cy-top);g.rotate(angle);
            g.drawImage(ink,origin,origin,side,side,null);
        } finally {g.dispose();}
        return new Layer(left,top,image);
    }
}
