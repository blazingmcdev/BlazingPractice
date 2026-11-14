package net.blazingpractice.arena;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Small WorldGuard adapter. WorldGuard is a soft dependency: the main plugin can still load
 * without it, but arena registration/validation is intentionally unavailable until it is installed.
 */
public final class WorldGuardBridge {
    private boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
    }

    public boolean regionExists(World world, String regionId) {
        if (!isAvailable() || world == null || regionId == null || regionId.isBlank()) return false;
        RegionManager manager = manager(world);
        return manager != null && manager.getRegion(regionId.trim()) != null;
    }

    public List<String> regionIds(World world) {
        if (!isAvailable() || world == null) return List.of();
        RegionManager manager = manager(world);
        if (manager == null) return List.of();
        return manager.getRegions().keySet().stream()
                .filter(id -> !id.equalsIgnoreCase("__global__"))
                .filter(id -> !id.equalsIgnoreCase("practice_lobby"))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public boolean contains(World world, String regionId, Location location) {
        if (!isAvailable() || world == null || location == null || location.getWorld() == null
                || !world.getUID().equals(location.getWorld().getUID())) return false;
        RegionManager manager = manager(world);
        if (manager == null) return false;
        ProtectedRegion region = manager.getRegion(regionId);
        return region != null && region.contains(BlockVector3.at(
                location.getBlockX(), location.getBlockY(), location.getBlockZ()));
    }

    private RegionManager manager(World world) {
        try {
            return WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(world));
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }
}
