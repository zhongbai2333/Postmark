package dev.postmark.model;

public record InkPoint(double x,double y) {
    public InkPoint {
        if(!Double.isFinite(x) || !Double.isFinite(y) || x<0 || x>1 || y<0 || y>1)
            throw new IllegalArgumentException("Signature point is outside the paper");
    }
}