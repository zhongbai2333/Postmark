package dev.postmark.model;

import java.util.List;

/** One counter interaction; initial inventory and legacy identity upgrades are not acquisitions. */
public final class StampAcquisition {
    private final String requested;
    private final List<StampDefinition> initial;
    private boolean delivered;
    public StampAcquisition(String requested,List<StampDefinition> initial) {
        this.requested=requested;this.initial=List.copyOf(initial);
    }
    public StampDefinition poll(List<StampDefinition> current) {
        if(delivered || requested==null) return null;
        var found=current.stream().filter(s->!s.practice() && s.key().equals(requested)).findFirst();
        if(found.isEmpty()) return null;
        delivered=true;
        var stamp=found.get();
        boolean owned=initial.stream().anyMatch(s->s.key().equals(requested)
                || s.key().equals(StampIdentity.slot(requested)) && s.asset().equals(stamp.asset()));
        return owned?null:stamp;
    }
}
