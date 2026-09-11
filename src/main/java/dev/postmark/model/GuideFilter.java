package dev.postmark.model;

import java.util.List;

public enum GuideFilter {
    ALL("全部"), MISSING("未收集"), UNSEARCHED("未逛过");
    private final String label;
    GuideFilter(String label){this.label=label;}
    public String label(){return label;}
    public boolean includes(boolean searched,List<TravelJournal.KnownStamp> stamps) {
        return switch(this) {
            case ALL -> true;
            case MISSING -> stamps.stream().anyMatch(s->!s.owned());
            case UNSEARCHED -> !searched;
        };
    }
}
