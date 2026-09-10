package dev.postmark.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** A server slot may contain several locally collected artwork variants over time. */
public final class StampIdentity {
    private StampIdentity() {}
    private static String encode(String value) {return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    public static String variant(String id,String item) {return item==null?id:"@"+encode(id)+"."+encode(item);}
    public static String key(UUID venue,String id,String item) {return venue+"/"+variant(id,item);}
    private static String[] parts(String key) {
        String tail=key.substring(key.indexOf('/')+1);
        if(tail.startsWith("@")) {
            int dot=tail.indexOf('.');
            if(dot>1)try {
                var decoder=Base64.getUrlDecoder();
                return new String[]{new String(decoder.decode(tail.substring(1,dot)),StandardCharsets.UTF_8),new String(decoder.decode(tail.substring(dot+1)),StandardCharsets.UTF_8)};
            }catch(IllegalArgumentException ignored) {}
        }
        return new String[]{tail,null};
    }
    public static String id(String key) {return parts(key)[0];}
    public static String item(String key) {return parts(key)[1];}
    public static String slot(String key) {int slash=key.indexOf('/');return slash<0?key:key.substring(0,slash+1)+id(key);}
}
