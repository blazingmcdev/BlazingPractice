package net.blazingpractice.lobby;

import net.blazingpractice.BlazingPractice;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Independent BlazingPractice lobby location, separate from any SMP plugin lobby. */
public final class PracticeLobby {
    private final BlazingPractice plugin;

    public PracticeLobby(BlazingPractice plugin) {
        this.plugin = plugin;
    }

    public boolean set(Player player) {
        if (plugin.worldProfiles() == null || !plugin.worldProfiles().setWorldRole("lobby", player.getWorld())) {
            return false;
        }
        Location location = player.getLocation();
        plugin.getConfig().set("lobby.location.world", location.getWorld().getName());
        plugin.getConfig().set("lobby.location.x", location.getX());
        plugin.getConfig().set("lobby.location.y", location.getY());
        plugin.getConfig().set("lobby.location.z", location.getZ());
        plugin.getConfig().set("lobby.location.yaw", location.getYaw());
        plugin.getConfig().set("lobby.location.pitch", location.getPitch());
        // setWorldRole has already assigned this world exclusively to the Practice lobby profile.
        plugin.saveConfig();
        if (plugin.protection() != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getWorld().equals(location.getWorld())) plugin.protection().protect(online);
            }
        }
        return true;
    }

    public Location location() {
        String worldName = plugin.getConfig().getString("lobby.location.world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null || !plugin.getConfig().contains("lobby.location.x")) return null;
        if (plugin.worldProfiles() == null || !plugin.worldProfiles().isLobbyWorld(world)) return null;
        return new Location(world,
                plugin.getConfig().getDouble("lobby.location.x"),
                plugin.getConfig().getDouble("lobby.location.y"),
                plugin.getConfig().getDouble("lobby.location.z"),
                (float) plugin.getConfig().getDouble("lobby.location.yaw"),
                (float) plugin.getConfig().getDouble("lobby.location.pitch"));
    }

    public boolean teleport(Player player) {
        Location location = location();
        if (location == null) return false;
        player.teleport(location);
        if (plugin.protection() != null) plugin.protection().protect(player);
        return true;
    }

    public boolean isSet() {
        return plugin.getConfig().contains("lobby.location.world");
    }

    public boolean isLobbyWorld(World world) {
        if (world == null || !isSet()) return false;
        return plugin.worldProfiles() != null && plugin.worldProfiles().isLobbyWorld(world);
    }
}
