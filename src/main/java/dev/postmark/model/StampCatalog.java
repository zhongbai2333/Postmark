package dev.postmark.model;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Community expectations are read-only hints, never discoveries or ownership. */
public final class StampCatalog {
    public enum Presence { PRESENT, ABSENT, UNKNOWN }
    public record Entry(UUID venue,int sourceRow,String name,Presence visitor,Presence expert,boolean confirmed) {
        public Entry { Objects.requireNonNull(visitor);Objects.requireNonNull(expert); }
        public List<TravelJournal.KnownStamp> merge(List<TravelJournal.KnownStamp> observed) {
            var result=new LinkedHashMap<String,TravelJournal.KnownStamp>();
            if(visitor==Presence.PRESENT)result.put("visitor",new TravelJournal.KnownStamp("visitor",null,null,false));
            if(expert==Presence.PRESENT)result.put("expert",new TravelJournal.KnownStamp("expert",null,null,false));
            for(var stamp:observed)result.put(stamp.id(),stamp);
            return List.copyOf(result.values());
        }
        public boolean complete(List<TravelJournal.KnownStamp> observed) {
            var merged=merge(observed);
            // Blank cells remain unknown unless an actual stamp resolves that slot.
            boolean visitorResolved=visitor!=Presence.UNKNOWN || observed.stream().anyMatch(s->s.id().equals("visitor"));
            boolean expertResolved=expert!=Presence.UNKNOWN || observed.stream().anyMatch(s->s.id().equals("expert"));
            return visitorResolved && expertResolved && !merged.isEmpty() && merged.stream().allMatch(TravelJournal.KnownStamp::owned);
        }
    }
    private final Map<UUID,Entry> entries;
    private final String date;
    private StampCatalog(String date,Map<UUID,Entry> entries) {this.date=date;this.entries=Map.copyOf(entries);}
    public Entry entry(UUID venue) {return entries.get(venue);}
    public int size() {return entries.size();}
    public String date() {return date;}
    public List<TravelJournal.KnownStamp> merge(UUID venue,List<TravelJournal.KnownStamp> observed) {
        var entry=entry(venue);return entry==null?observed:entry.merge(observed);
    }
    /** A completed area survey can supersede old hints; confirmed targets and discoveries remain. */
    public List<TravelJournal.KnownStamp> merge(UUID venue,List<TravelJournal.KnownStamp> observed,boolean areaSearched) {
        var entry=entry(venue);
        return areaSearched && (entry==null || !entry.confirmed())?List.copyOf(observed):merge(venue,observed);
    }
    /** Unknown corners remain silhouettes; preset data never supplies artwork or ownership. */
    public List<TravelJournal.KnownStamp> display(UUID venue,List<TravelJournal.KnownStamp> observed,boolean areaSearched) {
        var result=new LinkedHashMap<String,TravelJournal.KnownStamp>();
        var entry=entry(venue);
        if(!areaSearched)for(String id:List.of("visitor","expert")) {
            Presence expected=entry==null?Presence.UNKNOWN:id.equals("visitor")?entry.visitor():entry.expert();
            if(expected==Presence.UNKNOWN)result.put(id,new TravelJournal.KnownStamp(id,null,null,false));
        }
        for(var stamp:merge(venue,observed,areaSearched))result.put(stamp.id(),stamp);
        return List.copyOf(result.values());
    }
    public boolean complete(UUID venue,List<TravelJournal.KnownStamp> observed) {
        var entry=entry(venue);return entry==null?TravelJournal.regularComplete(observed):entry.complete(observed);
    }
    public boolean complete(UUID venue,List<TravelJournal.KnownStamp> observed,boolean areaSearched) {
        if(!areaSearched)return complete(venue,observed);
        var known=merge(venue,observed,true);
        return !known.isEmpty() && known.stream().allMatch(TravelJournal.KnownStamp::owned);
    }
    /** A partial or empty scan alone is not evidence of absence. */
    public boolean needsInspection(UUID venue,boolean searched,List<TravelJournal.KnownStamp> observed) {
        return needsInspection(venue,searched,observed,false);
    }
    public boolean needsInspection(UUID venue,boolean searched,List<TravelJournal.KnownStamp> observed,boolean areaSearched) {
        if(complete(venue,observed,areaSearched))return false;
        if(!searched)return true;
        var entry=entry(venue);
        if(areaSearched && (entry==null || !entry.confirmed()))return false;
        for(String id:List.of("visitor","expert")) {
            if(observed.stream().anyMatch(s->s.id().equals(id)))continue;
            Presence expected=entry==null?Presence.UNKNOWN:id.equals("visitor")?entry.visitor():entry.expert();
            if(expected!=Presence.ABSENT)return true;
        }
        return false;
    }
    public static StampCatalog bundled() {return Bundled.INSTANCE;}
    private static final class Bundled {private static final StampCatalog INSTANCE=load();}
    private static StampCatalog load() {
        try(var stream=StampCatalog.class.getResourceAsStream("/assets/postmark/catalogs/teacon2026.json")) {
            if(stream==null)throw new IOException("Missing stamp catalog");
            return read(new InputStreamReader(stream,StandardCharsets.UTF_8));
        } catch(IOException|RuntimeException e) {throw new IllegalStateException("Invalid bundled stamp catalog",e);}
    }
    public static StampCatalog read(Reader reader) {
        var root=JsonParser.parseReader(reader).getAsJsonObject();
        if(root.get("version").getAsInt()!=1)throw new IllegalArgumentException("Unsupported stamp catalog");
        var result=new HashMap<UUID,Entry>();
        for(var value:root.getAsJsonArray("entries")) {
            var row=value.getAsJsonObject();
            if(!row.has("venue"))continue; // Retain ambiguous source rows without assigning them to a gallery.
            var id=UUID.fromString(row.get("venue").getAsString());
            var entry=new Entry(id,row.get("sourceRow").getAsInt(),row.get("name").getAsString(),
                    Presence.valueOf(row.get("visitor").getAsString()),Presence.valueOf(row.get("expert").getAsString()),row.has("confirmedByExport"));
            if(result.put(id,entry)!=null)throw new IllegalArgumentException("Duplicate catalog venue: "+id);
        }
        return new StampCatalog(root.get("date").getAsString(),result);
    }
}
