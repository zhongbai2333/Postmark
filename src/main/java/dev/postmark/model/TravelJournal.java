package dev.postmark.model;

import java.util.*;

/** A footprint proves a completed local survey, never merely a teleport or an SMU visit mark. */
public record TravelJournal(int version, List<Entry> entries) {
    public record FoundStamp(String id,String item) {
        public FoundStamp {
            if(id==null || id.isBlank() || id.length()>64 || item==null || item.isBlank() || item.length()>512)
                throw new IllegalArgumentException("Invalid discovered stamp");
        }
    }
    public record Entry(UUID venue,boolean searched,List<FoundStamp> stamps,SurveyArea area) {
        public Entry(UUID venue,boolean searched,List<FoundStamp> stamps) {this(venue,searched,stamps,null);}
        public Entry {
            Objects.requireNonNull(venue); stamps=List.copyOf(stamps);
            if(area!=null && !searched)throw new IllegalArgumentException("Area survey requires inspection");
            if(stamps.stream().map(s->StampIdentity.variant(s.id,s.item)).distinct().count()!=stamps.size()) throw new IllegalArgumentException("Duplicate stamp variants");
        }
    }
    public record Discovery(UUID venue,String id,String item) {}
    public record KnownStamp(String id,String item,String asset,boolean owned,String expectedItem) {
        public KnownStamp(String id,String item,String asset,boolean owned) {this(id,item,asset,owned,null);}
        public String identity() {return StampIdentity.variant(id,item==null?expectedItem:item);}
    }
    public TravelJournal {
        if(version!=1) throw new IllegalArgumentException("Unsupported journal version: "+version);
        entries=List.copyOf(entries);
        if(entries.stream().map(Entry::venue).distinct().count()!=entries.size()) throw new IllegalArgumentException("Duplicate exhibition IDs");
    }
    public static TravelJournal empty() { return new TravelJournal(1,List.of()); }
    public Entry entry(UUID id) { return entries.stream().filter(e->e.venue.equals(id)).findFirst().orElse(new Entry(id,false,List.of())); }
    public TravelJournal surveyed(Set<UUID> searched,List<Discovery> discoveries) {
        var result=new LinkedHashMap<UUID,Entry>();for(var e:entries)result.put(e.venue,e);
        for(var d:discoveries) {
            var old=result.getOrDefault(d.venue,new Entry(d.venue,false,List.of()));
            var stamps=new LinkedHashMap<String,FoundStamp>();for(var s:old.stamps)stamps.put(StampIdentity.variant(s.id,s.item),s);
            stamps.put(StampIdentity.variant(d.id,d.item),new FoundStamp(d.id,d.item));
            result.put(d.venue,new Entry(d.venue,old.searched,List.copyOf(stamps.values()),old.area));
        }
        for(var id:searched) {var old=result.getOrDefault(id,new Entry(id,false,List.of()));result.put(id,new Entry(id,true,old.stamps,old.area));}
        return new TravelJournal(1,List.copyOf(result.values()));
    }
    public boolean areaSearched(GuideVenue venue) {var area=entry(venue.id()).area;return area!=null && area.matches(venue);}
    public TravelJournal areaSurveyed(Collection<GuideVenue> venues) {
        var result=new LinkedHashMap<UUID,Entry>();for(var e:entries)result.put(e.venue,e);
        for(var venue:venues)if(venue.canTeleport()) {
            var old=entry(venue.id());result.put(venue.id(),new Entry(venue.id(),true,old.stamps,SurveyArea.around(venue)));
        }
        return new TravelJournal(1,List.copyOf(result.values()));
    }
    public List<KnownStamp> stamps(UUID venue,List<StampDefinition> owned) {
        var result=new LinkedHashMap<String,KnownStamp>();
        for(var s:entry(venue).stamps)result.put(StampIdentity.variant(s.id,s.item),new KnownStamp(s.id,s.item,null,false));
        String prefix=venue+"/";
        for(var s:owned) if(!s.practice() && s.key().startsWith(prefix)) {
            String id=StampIdentity.id(s.key()),item=StampIdentity.item(s.key());
            result.put(StampIdentity.variant(id,item),new KnownStamp(id,item,s.asset(),true));
        }
        return List.copyOf(result.values());
    }
    /** The activity's regular visitor + expert target; a missing extra stamp revokes the mark. */
    public static boolean regularComplete(List<KnownStamp> stamps) {
        return stamps.stream().anyMatch(s->s.id.equals("visitor") && s.owned)
                && stamps.stream().anyMatch(s->s.id.equals("expert") && s.owned)
                && stamps.stream().allMatch(KnownStamp::owned);
    }
}
