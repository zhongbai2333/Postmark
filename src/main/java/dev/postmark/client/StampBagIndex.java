package dev.postmark.client;

import dev.postmark.model.StampDefinition;
import java.text.Normalizer;
import java.util.*;

/** Stable collection ordering and search, independent of visual magnification. */
public final class StampBagIndex {
    private StampBagIndex() {}
    public static List<StampDefinition> ordered(List<StampDefinition> stamps) {
        return stamps.stream().sorted(Comparator.comparing((StampDefinition s)->s.practice()?"":group(s))
                .thenComparing(StampDefinition::expert).thenComparing(StampDefinition::key)).toList();
    }
    private static String group(StampDefinition s) {
        int slash=s.key().lastIndexOf('/');return slash<0?s.key():s.key().substring(0,slash);
    }
    public static boolean matches(StampDefinition stamp,String query) {
        String haystack=normalize(stamp.name()+" "+stamp.key()+" "+(stamp.practice()?"启程":stamp.expert()?"大师 expert":"普通 visitor"));
        return Arrays.stream(normalize(query).strip().split("\\s+")).allMatch(haystack::contains);
    }
    private static String normalize(String value) { return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT); }
    public record Point(double x,double y) {}
    public static Point cell(int index,int columns) {
        return new Point((index%columns+(index/columns%2)*.5)*42,(index/columns)*36.373);
    }
}
