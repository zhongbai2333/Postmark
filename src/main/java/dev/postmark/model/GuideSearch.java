package dev.postmark.model;

import java.text.Normalizer;
import java.util.*;

public final class GuideSearch {
    private GuideSearch() {}
    private static String normalize(String text){return Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-+]+","");}
    public static boolean matches(GuideVenue venue,List<String> mods,String query) {
        if(query==null || query.isBlank())return true;
        String haystack=normalize(venue.name()+" "+venue.description()+" "+String.join(" ",mods));
        return Arrays.stream(query.strip().split("\\s+")).allMatch(word->haystack.contains(normalize(word)));
    }
}
