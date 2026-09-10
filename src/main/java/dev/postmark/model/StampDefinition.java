package dev.postmark.model;

public record StampDefinition(String key, String name, String asset, boolean practice) {
    public StampDefinition {
        if (key == null || name == null || asset == null) throw new IllegalArgumentException("Invalid stamp");
    }
    /** Activity stamp IDs are preserved after the exhibition UUID in the existing collection key. */
    public boolean expert() {
        return !practice && key.contains("/") && StampIdentity.id(key).equals("expert");
    }
}