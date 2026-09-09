package dev.postmark.render;

/** The displayed pixel rectangle is also the input coordinate system. */
public record PaperViewport(int x,int y,int width,int height) {
    public static PaperViewport fit(int screenWidth,int screenHeight,double ratio) {
        double maxW=Math.max(70,screenWidth-220),maxH=Math.max(45,screenHeight-100);
        int w=Math.max(1,(int)Math.round(Math.min(maxW,maxH*ratio)*.8f));
        int h=Math.max(1,(int)Math.round(w/ratio));
        return new PaperViewport((screenWidth-w)/2+12,(screenHeight-h)/2,w,h);
    }
    public double nx(double mouseX) {return (mouseX-x)/width;}
    public double ny(double mouseY) {return (mouseY-y)/height;}
}
