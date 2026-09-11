package dev.postmark.compat;

/** Data copied during extraction; rendering never queries live world or album state. */
public interface CounterCollectionState {
    boolean postmark$collected();
    void postmark$collected(boolean collected);
    boolean postmark$visible();
    long postmark$visibleSince();
}
