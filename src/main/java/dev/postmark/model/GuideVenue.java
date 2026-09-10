package dev.postmark.model;

import java.util.UUID;

/** Public SMU exhibition metadata. The server remains authoritative for teleportation. */
public record GuideVenue(UUID id, String name, String description, int x, int y, int z, float yaw, float pitch) {
    public boolean canTeleport() { return Float.isFinite(yaw) && Float.isFinite(pitch); }
    public String icon() { return "exhibition_portal:textures/gui/units/"+id.toString().replace("-", "")+"/icon.png"; }
    public double distanceSquared(double px,double pz) { return (px-x-.5)*(px-x-.5)+(pz-z-.5)*(pz-z-.5); }
}
