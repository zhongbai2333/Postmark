package dev.postmark.model;

import java.util.function.BiPredicate;

/** A bounded local search estimate, tied to the waypoint used when it was recorded. */
public record SurveyArea(int x,int y,int z,int radius) {
    public static final int RADIUS=64;
    public SurveyArea {if(radius<1 || radius>512)throw new IllegalArgumentException("Invalid survey radius");}
    public static SurveyArea around(GuideVenue venue) {return new SurveyArea(venue.x(),venue.y(),venue.z(),RADIUS);}
    public boolean matches(GuideVenue venue) {return venue.canTeleport() && equals(around(venue));}
    public boolean covered(BiPredicate<Integer,Integer> inspected) {
        for(int cz=Math.floorDiv(z-radius,16);cz<=Math.floorDiv(z+radius,16);cz++)
            for(int cx=Math.floorDiv(x-radius,16);cx<=Math.floorDiv(x+radius,16);cx++)
                if(!inspected.test(cx,cz))return false;
        return true;
    }
}
