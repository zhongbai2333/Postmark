package dev.postmark.model;

import java.util.List;

/** A mouse-written signature stroke in normalized paper coordinates. */
public record InkStroke(List<InkPoint> points,int color,double width) {
    public static final int MAX_POINTS=8000;
    public InkStroke {
        if(points==null || points.isEmpty() || points.size()>MAX_POINTS || !Double.isFinite(width)
                || width<.001 || width>.02 || (color>>>24)!=255) throw new IllegalArgumentException("Invalid signature stroke");
        points=List.copyOf(points);
    }
}