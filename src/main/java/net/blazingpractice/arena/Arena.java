package net.blazingpractice.arena;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Persistent configuration for one dedicated duel arena.
 * The WorldGuard region provides the arena boundary; the two locations are the fighter spawns.
 */
public record Arena(String name, String ladder, String region, World world,
                    Location cornerOne, Location cornerTwo,
                    Location spawnOne, Location spawnTwo, boolean enabled) {

    /** Backwards-compatible accessor used by older command integrations. */
    public String type() {
        return ladder;
    }

    public boolean isConfigured() {
        return enabled && world != null && ladder != null && !ladder.isBlank()
                && region != null && !region.isBlank()
                && spawnOne != null && spawnTwo != null
                && spawnOne.getWorld() != null && spawnTwo.getWorld() != null
                && world.getUID().equals(spawnOne.getWorld().getUID())
                && world.getUID().equals(spawnTwo.getWorld().getUID());
    }

    /** Legacy cuboid test retained for compatibility with older arena metadata. */
    public boolean contains(Location location) {
        if (location == null || world == null || location.getWorld() == null
                || !world.getUID().equals(location.getWorld().getUID())
                || cornerOne == null || cornerTwo == null) return false;
        double minX = Math.min(cornerOne.getX(), cornerTwo.getX());
        double maxX = Math.max(cornerOne.getX(), cornerTwo.getX());
        double minY = Math.min(cornerOne.getY(), cornerTwo.getY());
        double maxY = Math.max(cornerOne.getY(), cornerTwo.getY());
        double minZ = Math.min(cornerOne.getZ(), cornerTwo.getZ());
        double maxZ = Math.max(cornerOne.getZ(), cornerTwo.getZ());
        return location.getX() >= minX && location.getX() <= maxX
                && location.getY() >= minY && location.getY() <= maxY
                && location.getZ() >= minZ && location.getZ() <= maxZ;
    }
}
