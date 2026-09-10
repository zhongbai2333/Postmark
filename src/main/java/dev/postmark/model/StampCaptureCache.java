package dev.postmark.model;

import java.util.*;

/** Identity tickets prevent an obsolete asynchronous capture from restoring a revoked stamp. */
public final class StampCaptureCache {
    public record Ticket(String item) {}
    private final Map<String,Ticket> tickets=new HashMap<>();
    public Ticket get(String key) {return tickets.get(key);}
    public Ticket start(String key,String item) {var ticket=new Ticket(item);tickets.put(key,ticket);return ticket;}
    public boolean current(String key,Ticket ticket) {return tickets.get(key)==ticket;}
    public void retain(Set<String> owned) {tickets.keySet().retainAll(owned);}
    public void failed(String key,Ticket ticket) {if(current(key,ticket))tickets.remove(key);}
    public void clear() {tickets.clear();}
}
