package dev.postmark.model;

import java.util.*;

/** Collected identities, never imprint count or an estimate of all obtainable stamps. */
public record CollectionProgress(int total,int visitors,int experts,int others,int venues) {
    public static CollectionProgress from(List<StampDefinition> stamps) {
        Map<String,StampDefinition> unique=new HashMap<>();
        for(var stamp:stamps) if(!stamp.practice()) unique.put(stamp.key(),stamp);
        int visitors=0,experts=0,others=0;Set<String> venues=new HashSet<>();
        for(var stamp:unique.values()) {
            int slash=stamp.key().lastIndexOf('/');
            String id=StampIdentity.id(stamp.key());
            if(slash>0) venues.add(stamp.key().substring(0,slash));
            switch(id) { case "visitor" -> visitors++;case "expert" -> experts++;default -> others++; }
        }
        return new CollectionProgress(unique.size(),visitors,experts,others,venues.size());
    }
    public int target() { return Math.max(10,((total+9)/10)*10); }
    public int holes() { return total==0?0:(total-1)%10+1; }
    public int milestone() { return total/10*10; }
    public boolean unlocks(int milestone) { return milestone>=10 && milestone%10==0 && milestone<=milestone(); }
}
