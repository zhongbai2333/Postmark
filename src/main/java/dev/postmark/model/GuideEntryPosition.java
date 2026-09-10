package dev.postmark.model;

/** Relative position within the visible screen, independent of world and GUI scale. */
public record GuideEntryPosition(double horizontal,double vertical) {
    public GuideEntryPosition {
        if(!Double.isFinite(horizontal) || !Double.isFinite(vertical))throw new IllegalArgumentException("Invalid guide position");
        horizontal=Math.clamp(horizontal,0,1);vertical=Math.clamp(vertical,0,1);
    }
    private static int margin(int screen,int widget) {return Math.min(6,Math.max(0,(screen-widget)/2));}
    private static int span(int screen,int widget) {return Math.max(0,screen-widget-2*margin(screen,widget));}
    public int x(int screen,int widget) {return margin(screen,widget)+(int)Math.round(horizontal*span(screen,widget));}
    public int y(int screen,int widget) {return margin(screen,widget)+(int)Math.round(vertical*span(screen,widget));}
    public static GuideEntryPosition at(int x,int y,int width,int height,int widgetWidth,int widgetHeight) {
        return new GuideEntryPosition((x-margin(width,widgetWidth))/(double)Math.max(1,span(width,widgetWidth)),
                (y-margin(height,widgetHeight))/(double)Math.max(1,span(height,widgetHeight)));
    }
}
