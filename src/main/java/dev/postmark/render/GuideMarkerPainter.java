package dev.postmark.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Bake the existing pixel stickers once; each displayed marker then needs just one textured quad. */
public final class GuideMarkerPainter {
    private GuideMarkerPainter() {}
    private static void fill(Graphics2D g,int x1,int y1,int x2,int y2,int color) {g.setColor(new Color(color,true));g.fillRect(x1,y1,x2-x1,y2-y1);}
    public static BufferedImage paint() {
        var image=new BufferedImage(384,128,BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();PostcardPainter.pixel(g);g.scale(2,2);
        g.translate(32,32);footprint(g);g.translate(64,0);inspection(g);g.translate(64,0);complete(g);g.dispose();return image;
    }
    public static BufferedImage paperPatterns() {
        var image=new BufferedImage(560,140,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();PostcardPainter.pixel(g);
        for(int kind=0;kind<4;kind++) {int x=kind*140+12,y=12,color=0x227E987B;
            if(kind==0){ // Terraced mountains, like a faint map printed on the paper.
                for(int i=0;i<4;i++){int px=x+i*10,py=y+36-i*7;fill(g,px,py,px+11,py+2,color);fill(g,px+9,py-6,px+11,py+2,color);}
                for(int i=0;i<3;i++)fill(g,x+41+i*8,y+16+i*7,x+50+i*8,y+18+i*7,color);
            }else if(kind==1){
                for(int i=0;i<5;i++){int py=y+i*9,px=x+(i%2)*7;fill(g,px,py,px+42,py+2,0x207D9C9D);fill(g,px+41,py,px+43,py+5,0x207D9C9D);}
            }else if(kind==2){
                fill(g,x+22,y+12,x+25,y+50,color);
                for(int i=0;i<3;i++){int py=y+i*12;fill(g,x+7+i*4,py+9,x+39-i*3,py+16,color);fill(g,x+13+i*4,py+4,x+32-i*2,py+9,color);}
            }else {
                for(int i=0;i<38;i+=6){fill(g,x+i,y+4,x+i+2,y+6,0x228F7954);fill(g,x+38,y+i/2+5,x+40,y+i/2+7,0x228F7954);}
                fill(g,x+36,y+28,x+43,y+30,0x228F7954);fill(g,x+39,y+25,x+41,y+33,0x228F7954);
            }
            fill(g,x+78,y+63,x+82,y+64,0x168A714C);fill(g,x+95,y+91,x+97,y+93,0x168A714C);
        }
        g.dispose();return image;
    }
    private static void footprint(Graphics2D g) {
        var pose=g.getTransform();g.rotate(.12);
        fill(g,-15,-16,19,20,0x503D3022);
        fill(g,-16,-18,16,18,0xFF785139);fill(g,-18,-16,18,16,0xFF785139);
        fill(g,-15,-16,15,16,0xFFF3E4BA);fill(g,-16,-14,16,14,0xFFF3E4BA);
        paw(g,-7,5,1.25,-.28,0xFF80503A);paw(g,7,-7,1,.32,0xFF986348);g.setTransform(pose);
    }
    private static void paw(Graphics2D g,int x,int y,double scale,double angle,int color) {
        String[] pixels={"0001100110000","0011100111000","0011100111000","0001000010000",
                "1100000000011","1110000000111","0110000000110","0000011100000",
                "0000111110000","0001111111000","0001111111000","0000110110000"};
        var pose=g.getTransform();g.translate(x,y);g.rotate(angle);g.scale(scale,scale);
        for(int row=0;row<pixels.length;row++)for(int col=0;col<13;col++)if(pixels[row].charAt(col)=='1')fill(g,col-6,row-6,col-5,row-5,color);
        g.setTransform(pose);
    }
    private static void inspection(Graphics2D g) {
        fill(g,-10,-12,12,14,0x503D3022);fill(g,-11,-14,11,12,0xFF92653B);fill(g,-9,-12,9,10,0xFFF0CC79);
        String[] mark={"01110","11011","00011","00110","00100","00000","00100"};
        for(int row=0;row<mark.length;row++)for(int col=0;col<5;col++)if(mark[row].charAt(col)=='1')fill(g,-5+col*2,-7+row*2,-3+col*2,-5+row*2,0xFF63432D);
    }
    private static void complete(Graphics2D g) {
        // Keep adjacent scanlines on the same pixel grid; rotating each row separately leaves seams.
        // The GUI rotates the completed texture as a single quad instead.
        disc(g,1,2,13,0x60413424);disc(g,0,0,13,0xFFF7EAC6);disc(g,0,0,11,0xFF356647);disc(g,0,0,9,0xFFF6EBD0);
        line(g,-6,0,-2,4);line(g,-2,4,6,-5);
    }
    private static void disc(Graphics2D g,int x,int y,int r,int color) {
        for(int row=-r;row<=r;row++){int half=(int)Math.sqrt(r*r-row*row);fill(g,x-half,y+row,x+half+1,y+row+1,color);}
    }
    private static void line(Graphics2D g,int x1,int y1,int x2,int y2) {
        int steps=Math.max(Math.abs(x2-x1),Math.abs(y2-y1));
        for(int i=0;i<=steps;i++){int x=x1+Math.round((x2-x1)*i/(float)steps),y=y1+Math.round((y2-y1)*i/(float)steps);fill(g,x-1,y-1,x+2,y+2,0xFF285638);}
    }
}
