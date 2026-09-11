package dev.postmark.model;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Community expectations are read-only hints, never discoveries or ownership. */
public final class StampCatalog {
    public enum Presence { PRESENT, ABSENT, UNKNOWN }
    public record ExpectedStamp(String id,String item) {}
    public record Entry(UUID venue,int sourceRow,String name,Presence visitor,Presence expert,boolean confirmed,List<ExpectedStamp> artworks) {
        public Entry(UUID venue,int sourceRow,String name,Presence visitor,Presence expert,boolean confirmed) {this(venue,sourceRow,name,visitor,expert,confirmed,List.of());}
        public Entry { Objects.requireNonNull(visitor);Objects.requireNonNull(expert);artworks=List.copyOf(artworks); }
        public List<TravelJournal.KnownStamp> merge(List<TravelJournal.KnownStamp> observed) {
            var result=new LinkedHashMap<String,TravelJournal.KnownStamp>();
            if(visitor==Presence.PRESENT)result.put("visitor",new TravelJournal.KnownStamp("visitor",null,null,false));
            if(expert==Presence.PRESENT)result.put("expert",new TravelJournal.KnownStamp("expert",null,null,false));
            for(var expected:artworks) {
                result.remove(expected.id());
                var placeholder=new TravelJournal.KnownStamp(expected.id(),null,null,false,expected.item());
                result.put(placeholder.identity(),placeholder);
            }
            for(var stamp:observed)putObserved(result,stamp);
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
    private static void putObserved(Map<String,TravelJournal.KnownStamp> result,TravelJournal.KnownStamp stamp) {
        // Replace a category placeholder, never another actual artwork in that category.
        var placeholder=result.get(stamp.id());
        if(placeholder!=null && placeholder.item()==null && placeholder.asset()==null)result.remove(stamp.id());
        result.put(stamp.identity(),stamp);
    }
    private final Map<UUID,Entry> entries;
    private final String date;
    private final Map<UUID,List<String>> mods;
    private StampCatalog(String date,Map<UUID,Entry> entries,Map<UUID,List<String>> mods) {this.date=date;this.entries=Map.copyOf(entries);this.mods=Map.copyOf(mods);}
    public List<String> mods(UUID venue){return mods.getOrDefault(venue,List.of());}
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
        for(var stamp:merge(venue,observed,areaSearched))putObserved(result,stamp);
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
        if(merge(venue,observed,areaSearched).stream().anyMatch(s->s.expectedItem()!=null && s.item()==null && !s.owned()))return true;
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
        var result=new HashMap<UUID,Entry>();var mods=new HashMap<UUID,List<String>>();
        for(var value:root.getAsJsonArray("entries")) {
            var row=value.getAsJsonObject();
            if(!row.has("venue"))continue; // Retain ambiguous source rows without assigning them to a gallery.
            var id=UUID.fromString(row.get("venue").getAsString());
            var names=new ArrayList<String>();if(row.has("mods"))for(var mod:row.getAsJsonArray("mods"))names.add(mod.getAsString());mods.put(id,List.copyOf(names));
            var artworks=new ArrayList<ExpectedStamp>();
            if(row.has("confirmedArtworks"))for(var artwork:row.getAsJsonArray("confirmedArtworks")) {
                var a=artwork.getAsJsonObject();artworks.add(new ExpectedStamp(a.get("id").getAsString(),a.get("item").getAsString()));
            }
            var entry=new Entry(id,row.get("sourceRow").getAsInt(),row.get("name").getAsString(),
                    Presence.valueOf(row.get("visitor").getAsString()),Presence.valueOf(row.get("expert").getAsString()),row.has("confirmedByExport"),artworks);
            if(result.put(id,entry)!=null)throw new IllegalArgumentException("Duplicate catalog venue: "+id);
        }
        return new StampCatalog(root.get("date").getAsString(),result,mods);
    }
}
