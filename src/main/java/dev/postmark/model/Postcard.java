package dev.postmark.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record Postcard(UUID id,String title,String background,List<Imprint> imprints,List<InkStroke> signature,int width,int height) {
    public static final int MAX_IMPRINTS=2048, MAX_STROKES=256, MAX_SIGNATURE_POINTS=50000;
    public Postcard {
        // Missing dimensions in earlier drafts retain their original 3:2 composition.
        if(width==0 && height==0) { width=1800;height=1200; }
        if(width<1 || height<1 || width>1800 || height>1800) throw new IllegalArgumentException("Invalid paper dimensions");
        if(id==null || title==null || title.length()>160 || imprints==null || imprints.size()>MAX_IMPRINTS)
            throw new IllegalArgumentException("Invalid postcard");
        imprints=List.copyOf(imprints);
        // Version-1 drafts from alpha.1 have no signature field; retain their existing front unchanged.
        signature=signature==null?List.of():List.copyOf(signature);
        if(signature.size()>MAX_STROKES || signature.stream().mapToInt(s->s.points().size()).sum()>MAX_SIGNATURE_POINTS)
            throw new IllegalArgumentException("Signature is too large");
    }
    public Postcard(UUID id,String title,String background,List<Imprint> imprints,List<InkStroke> signature) { this(id,title,background,imprints,signature,1800,1200); }
    public double aspectRatio() { return (double)width/height; }
    public Postcard(UUID id,String title,String background,List<Imprint> imprints) { this(id,title,background,imprints,List.of()); }
    public static Postcard blank() { return new Postcard(UUID.randomUUID(),"旅途来信",null,List.of(),List.of()); }
    public Postcard stamp(String source,String asset,double x,double y,double size,double angle) {
        var next=new ArrayList<>(imprints);
        next.add(new Imprint(UUID.randomUUID(),source,asset,x,y,size,angle));
        return new Postcard(id,title,background,next,signature,width,height);
    }
    public Postcard erase(UUID imprintId) {
        return new Postcard(id,title,background,imprints.stream().filter(s->!s.id().equals(imprintId)).toList(),signature,width,height);
    }
    public Imprint topAt(double x,double y) {
        for(var mark:imprints.reversed()) if(mark.contains(x,y,aspectRatio())) return mark;
        return null;
    }
    public Postcard withBackground(String asset) { return new Postcard(id,title,asset,imprints,signature); }
    public Postcard withBackground(String asset,int imageWidth,int imageHeight,boolean crop) {
        if(imageWidth<1 || imageHeight<1 || imageWidth>8192 || imageHeight>8192) throw new IllegalArgumentException("Invalid photo dimensions");
        if(crop || asset==null) return withBackground(asset);
        double scale=1800.0/Math.max(imageWidth,imageHeight);
        return new Postcard(id,title,asset,imprints,signature,Math.max(1,(int)Math.round(imageWidth*scale)),Math.max(1,(int)Math.round(imageHeight*scale)));
    }
    public Postcard withSignature(List<InkStroke> strokes) { return new Postcard(id,title,background,imprints,strokes,width,height); }
}