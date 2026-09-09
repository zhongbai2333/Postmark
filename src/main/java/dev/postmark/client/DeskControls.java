package dev.postmark.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small physical desk objects, with descriptions supplied by the owning screen's hover line. */
public final class DeskControls {
    public enum Kind { NEW_PAPER, PLAIN_PAPER, FOLDER, BOOK, DELETE, CONFIRM, UNDO, CLEAR, CLOSE }
    private DeskControls() {}
    public static boolean hit(double mx,double my,double x,double y) { return Math.abs(mx-x)<=13 && Math.abs(my-y)<=13; }
    public static void draw(GuiGraphicsExtractor g,Kind kind,int x,int y,boolean enabled) {
        int ink=enabled?0xFF65735B:0x887B7B65;
        if(kind==Kind.CLOSE) {
            for(int i=-4;i<=4;i++) { g.fill(x+i,y+i,x+i+2,y+i+2,0xAACFC6AC);g.fill(x+i,y-i,x+i+2,y-i+2,0xAACFC6AC); }return;
        }
        g.fill(x-7,y-7,x+12,y+12,0x3032261B);
        switch(kind) {
            case NEW_PAPER, PLAIN_PAPER -> {
                g.fill(x-7,y-8,x+11,y+11,0xFFBCAA87);g.fill(x-10,y-11,x+8,y+8,0xFFF0E5CB);
                g.fill(x-8,y-9,x+6,y-7,0xFFFFF3D6);
                if(kind==Kind.NEW_PAPER) { g.fill(x-4,y-5,x-2,y+3,ink);g.fill(x-7,y-2,x+1,y,ink); }
                else { g.fill(x-6,y-3,x+4,y+3,0xFFADC19C);g.fill(x-5,y-5,x-2,y-2,0xFFD7BE79);g.fill(x+2,y+3,x+9,y+7,ink); }
            }
            case BOOK -> {
                g.fill(x-12,y-10,x+11,y+11,0xFF694B36);g.fill(x-10,y-11,x+9,y+8,0xFFA47E50);
                g.fill(x-7,y-9,x+7,y+6,0xFFE7D5AC);g.fill(x-9,y-11,x-6,y+8,0xFF765638);
                g.fill(x-3,y-5,x,y-2,ink);g.fill(x+2,y-5,x+5,y-2,ink);g.fill(x-3,y+1,x+5,y+3,ink);
            }
            case DELETE -> {
                g.fill(x-6,y-4,x+6,y+8,0xFFAA7860);g.fill(x-8,y-7,x+8,y-4,0xFFC39B77);
                g.fill(x-3,y-10,x+3,y-7,0xFF89654D);g.fill(x-3,y-2,x-1,y+5,0xFFE2C7A4);g.fill(x+2,y-2,x+4,y+5,0xFFE2C7A4);
            }
            case CONFIRM -> {
                g.fill(x-9,y-9,x+10,y+10,0xFFEDE1C4);
                for(int i=0;i<4;i++) g.fill(x-6+i,y+i-1,x-4+i,y+i+1,ink);
                for(int i=0;i<8;i++) g.fill(x-3+i,y+3-i,x-1+i,y+5-i,ink);
            }
            case FOLDER -> {
                g.fill(x-10,y-7,x-2,y-4,0xFFC7A469);g.fill(x-10,y-4,x+11,y+9,0xFFBD9256);
                g.fill(x-11,y-2,x+12,y+9,0xFFE0BE81);g.fill(x-10,y,x+11,y+2,0xFFF0D197);
            }
            case UNDO -> {
                g.fill(x-8,y-9,x+9,y+10,0xFFEDE1C4);g.fill(x-5,y-3,x+5,y-1,ink);
                g.fill(x+3,y-1,x+5,y+4,ink);g.fill(x-2,y+3,x+5,y+5,ink);
                for(int i=0;i<3;i++) g.fill(x-6+i,y-2-i,x-4+i,y+i,ink);
            }
            case CLEAR -> {
                g.fill(x-10,y-7,x+10,y+6,0xFFD9A28B);g.fill(x-10,y+6,x+10,y+9,0xFFAE7E69);
                g.fill(x-5,y-8,x+5,y+6,0xFFF4E8CD);g.fill(x-5,y+4,x+5,y+8,ink);
                g.fill(x-2,y-4,x+2,y-2,ink);
            }
            default -> {}
        }
    }
}
