package dev.postmark.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record Album(int version, UUID current, List<Postcard> cards, List<StampDefinition> stamps) {
    public Album {
        if (version != 1 || cards == null || cards.isEmpty() || cards.size() > 512
                || stamps == null || stamps.size() > 4096
                || cards.stream().noneMatch(c -> c.id().equals(current)))
            throw new IllegalArgumentException("Invalid or unsupported album");
        cards = List.copyOf(cards);
        stamps = List.copyOf(stamps);
    }
    public static Album empty() {
        Postcard card = Postcard.blank();
        return new Album(1, card.id(), List.of(card), List.of());
    }
    public Postcard selected() { return cards.stream().filter(c -> c.id().equals(current)).findFirst().orElseThrow(); }
    public Album replace(Postcard card) {
        return new Album(version, current, cards.stream().map(c -> c.id().equals(card.id()) ? card : c).toList(), stamps);
    }
    public Album addCard() {
        var next = new ArrayList<>(cards);
        Postcard card = Postcard.blank(); next.add(card);
        return new Album(version, card.id(), next, stamps);
    }
    public Album select(int offset) {
        int i = cards.indexOf(selected());
        return new Album(version, cards.get(Math.floorMod(i + offset, cards.size())).id(), cards, stamps);
    }
    public Album select(UUID id) {
        if(cards.stream().noneMatch(c->c.id().equals(id))) throw new IllegalArgumentException("Unknown postcard");
        return new Album(version,id,cards,stamps);
    }
    public Album remove(UUID id) {
        int index=-1;for(int i=0;i<cards.size();i++) if(cards.get(i).id().equals(id)) {index=i;break;}
        if(index<0) return this;
        var next=new ArrayList<>(cards);next.remove(index);
        if(next.isEmpty()) next.add(Postcard.blank());
        UUID selected=current.equals(id)?next.get(Math.min(index,next.size()-1)).id():current;
        return new Album(version,selected,next,stamps);
    }
    public Album unlock(StampDefinition stamp) {
        var next = new ArrayList<>(stamps);
        int existing=-1;
        for(int i=0;i<next.size();i++) if(next.get(i).key().equals(stamp.key())) { existing=i;break; }
        if(existing>=0) next.set(existing,stamp);else next.add(stamp);
        return new Album(version, current, cards, next);
    }
}