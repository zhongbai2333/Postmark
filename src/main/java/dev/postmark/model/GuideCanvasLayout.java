package dev.postmark.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Stable, uncolumned paper scraps, with room for enlarged names and corner stamps. */
public record GuideCanvasLayout(List<Node> nodes,double width,double height) {
    public record Node(int index,int x,int y,int size,int variant,float angle) {}
    public static GuideCanvasLayout scatter(List<UUID> ids) {
        var raw=new ArrayList<Node>();double minX=0,minY=0,maxX=240,maxY=180;
        for(int i=0;i<ids.size();i++) {
            var id=ids.get(i);var random=new Random(id.getMostSignificantBits()^id.getLeastSignificantBits());
            double theta=i*2.399963229728653,radius=94*Math.sqrt(i+.5);
            int size=68+random.nextInt(17),x=(int)Math.round(Math.cos(theta)*radius)+random.nextInt(11)-5,
                y=(int)Math.round(Math.sin(theta)*radius)+random.nextInt(11)-5;
            raw.add(new Node(i,x-size/2,y-size/2,size,random.nextInt(4),(random.nextFloat()-.5f)*.20f));
            minX=Math.min(minX,x-85);minY=Math.min(minY,y-85);maxX=Math.max(maxX,x+85);maxY=Math.max(maxY,y+105);
        }
        var nodes=new ArrayList<Node>();
        for(var n:raw)nodes.add(new Node(n.index,n.x-(int)minX+25,n.y-(int)minY+25,n.size,n.variant,n.angle));
        return new GuideCanvasLayout(List.copyOf(nodes),maxX-minX+50,maxY-minY+50);
    }
}
