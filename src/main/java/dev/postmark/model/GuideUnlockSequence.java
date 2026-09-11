package dev.postmark.model;

import java.util.*;

/** Only visible, render-ready stamps enter the sequence; closing midway does not mark them seen. */
public final class GuideUnlockSequence {
    public static final long DURATION=550,INTERVAL=220;
    private final Set<String> seen=new HashSet<>();
    private Set<String> owned=Set.of();
    private final Map<String,Long> active=new LinkedHashMap<>();
    private long next;
    public GuideUnlockSequence(Set<String> seen){this.seen.addAll(seen);}
    public void sync(Collection<String> keys){owned=Set.copyOf(keys);seen.retainAll(owned);active.keySet().retainAll(owned);}
    public List<String> advance(long now,List<String> visible) {
        var finished=new ArrayList<String>();
        var iterator=active.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();if(now-entry.getValue()>=DURATION){seen.add(entry.getKey());finished.add(entry.getKey());iterator.remove();}}
        if(now>=next)for(String key:visible)if(owned.contains(key)&&!seen.contains(key)&&!active.containsKey(key)){active.put(key,now);next=now+INTERVAL;break;}
        return finished;
    }
    public boolean pending(String key){return owned.contains(key)&&!seen.contains(key);}
    public double amount(String key,long now){return !pending(key)?1:active.containsKey(key)?Math.clamp((now-active.get(key))/(double)DURATION,0,1):0;}
    public int activeCount(){return active.size();}
}
