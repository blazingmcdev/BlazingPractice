package net.blazingpractice.ffa;

import net.blazingpractice.BlazingPractice;
import net.blazingpractice.world.WorldProfileSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** FFA is a player mode, not simply a world identity. This permits FFA and Practice to share one world. */
public final class FfaService implements Listener {
    private final BlazingPractice plugin;
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    /** Practice/world inventory to restore after leaving FFA in the shared PvP world. */
    private final Map<UUID, WorldProfileSnapshot> returnSnapshots = new HashMap<>();
    private final Set<UUID> activePlayers = new HashSet<>();
    /** Selected FFA ladder for each active player; kit application is a later gameplay milestone. */
    private final Map<UUID, String> selectedModes = new HashMap<>();

    public FfaService(BlazingPractice plugin) {
        this.plugin = plugin;
    }

    public boolean join(Player player) {
        return join(player, "sword");
    }

    /** Join FFA after selecting a ladder in the FFA mode menu. */
    public boolean join(Player player, String ladderId) {
        var selectedLadder = plugin.ladders() == null ? java.util.Optional.<net.blazingpractice.ladder.LadderRegistry.Ladder>empty()
                : plugin.ladders().find(ladderId);
        if (selectedLadder.isEmpty() || !selectedLadder.get().enabled()) {
            player.sendMessage(color("&cThat FFA mode is not available. Choose an enabled mode from the FFA menu."));
            return false;
        }
        if (!plugin.getConfig().getBoolean("ffa.enabled", true)) {
            player.sendMessage(color("&cFFA is currently disabled."));
            return false;
        }
        if (isActive(player)) {
            player.sendMessage(color("&eYou are already playing FFA."));
            return true;
        }
        if (plugin.duels() != null && plugin.duels().isInMatch(player)) {
            player.sendMessage(color("&cYou cannot enter FFA while you are in a duel."));
            return false;
        }
        World target = destinationWorld();
        if (target == null) {
            player.sendMessage(color("&cNo PvP world is loaded. Run /bp world set pvp while standing in your shared Practice/FFA world."));
            return false;
        }

        UUID uuid = player.getUniqueId();
        returnLocations.put(uuid, player.getLocation().clone());
        selectedModes.put(uuid, selectedLadder.get().id());
        if (plugin.queues() != null) plugin.queues().leave(player);
        player.closeInventory();
        activePlayers.add(uuid);
        if (plugin.protection() != null) plugin.protection().release(player);

        if (!player.teleport(spawnLocation(target))) {
            activePlayers.remove(uuid);
            returnLocations.remove(uuid);
            returnSnapshots.remove(uuid);
            selectedModes.remove(uuid);
            if (plugin.protection() != null && plugin.protection().isSafeWorld(player.getWorld().getName())) {
                plugin.protection().protect(player);
            }
            player.sendMessage(color("&cYour teleport to the FFA spawn was cancelled. Safe-zone protection has been restored."));
            return false;
        }

        // The Practice profile must finish loading before we snapshot it. This matters
        // when a player enters FFA from the lobby or Survival using the shared PvP world.
        plugin.worldProfiles().whenReady(player, () -> {
            if (!player.isOnline() || !isActive(player)) return;
            returnSnapshots.put(uuid, WorldProfileSnapshot.capture(player));
            clearFfaInventory(player);
            if (plugin.selector() != null) plugin.selector().ensureForWorld(player);
            player.sendMessage(color("&aYou joined &6" + selectedLadder.get().name() + " FFA&a. &7Use the barrier or &e/ffa leave &7to return with your previous inventory restored."));
        });
        return true;
    }

    public boolean leave(Player player) {
        UUID uuid = player.getUniqueId();
        if (!isActive(player)) {
            player.sendMessage(color("&cYou are not currently playing FFA."));
            return false;
        }
        if (!returnSnapshots.containsKey(uuid)) {
            player.sendMessage(color("&eFFA is still preparing your separate inventory. Try again in a moment."));
            return false;
        }
        Location destination = returnLocations.get(uuid);
        if (destination == null || destination.getWorld() == null) {
            destination = plugin.lobby() == null ? null : plugin.lobby().location();
        }
        if (destination == null || destination.getWorld() == null) {
            destination = plugin.worldProfiles() == null ? null : plugin.worldProfiles().practiceSpawnLocation();
        }
        if (destination == null || destination.getWorld() == null) {
            player.sendMessage(color("&cNo return location is available. Set the Practice lobby or Practice spawn."));
            return false;
        }

        WorldProfileSnapshot ffaState = WorldProfileSnapshot.capture(player);
        WorldProfileSnapshot returnState = returnSnapshots.get(uuid);
        if (returnState != null) returnState.apply(player);
        else clearFfaInventory(player);

        if (!player.teleport(destination)) {
            ffaState.apply(player);
            player.sendMessage(color("&cYour return teleport was cancelled; you remain in FFA."));
            return false;
        }

        activePlayers.remove(uuid);
        returnLocations.remove(uuid);
        returnSnapshots.remove(uuid);
        selectedModes.remove(uuid);
        if (plugin.protection() != null && plugin.protection().isSafeWorld(player.getWorld().getName())) {
            plugin.protection().protect(player);
        }
        refreshModeItems(player);
        player.sendMessage(color("&eYou left FFA. Your previous world inventory has been restored."));
        return true;
    }

    /** Supplies the non-FFA Practice profile when the server saves while a player is in FFA. */
    public WorldProfileSnapshot snapshotForWorldSave(Player player) {
        if (player != null && isActive(player)) {
            WorldProfileSnapshot original = returnSnapshots.get(player.getUniqueId());
            if (original != null) return original;
        }
        return WorldProfileSnapshot.capture(player);
    }

    private void clearFfaInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.setLevel(0);
        player.setExp(0.0f);
        player.setTotalExperience(0);
        for (var effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
    }

    public boolean setSpawn(Player player) {
        if (plugin.worldProfiles() == null || !plugin.worldProfiles().isFfaWorld(player.getWorld())) {
            player.sendMessage(color("&cStand in the configured shared PvP world first. Use /bp world set pvp, then /ffa setspawn."));
            return false;
        }
        Location location = player.getLocation();
        String path = "ffa.spawn.";
        plugin.getConfig().set(path + "world", location.getWorld().getName());
        plugin.getConfig().set(path + "x", location.getX());
        plugin.getConfig().set(path + "y", location.getY());
        plugin.getConfig().set(path + "z", location.getZ());
        plugin.getConfig().set(path + "yaw", location.getYaw());
        plugin.getConfig().set(path + "pitch", location.getPitch());
        plugin.saveConfig();
        player.sendMessage(color("&aFFA spawn saved in &f" + location.getWorld().getName() + "&a."));
        return true;
    }

    public String selectedMode(Player player) {
        return player == null ? null : selectedModes.get(player.getUniqueId());
    }

    public boolean isFfaWorld(World world) {
        return world != null && plugin.worldProfiles() != null && plugin.worldProfiles().isFfaWorld(world);
    }

    /** True only after the player explicitly joins FFA (or joins a legacy dedicated FFA world). */
    public boolean isActive(Player player) {
        return player != null && activePlayers.contains(player.getUniqueId());
    }

    public int onlineCount() {
        return (int) Bukkit.getOnlinePlayers().stream().filter(this::isActive).count();
    }

    public Location spawnLocation(World destination) {
        String configuredWorld = plugin.getConfig().getString("ffa.spawn.world", "");
        World spawnWorld = configuredWorld.isBlank() ? destination : Bukkit.getWorld(configuredWorld);
        if (spawnWorld == null || !isFfaWorld(spawnWorld)) spawnWorld = destination;
        String path = "ffa.spawn.";
        if (plugin.getConfig().contains(path + "x")) {
            return new Location(spawnWorld,
                    plugin.getConfig().getDouble(path + "x"),
                    plugin.getConfig().getDouble(path + "y"),
                    plugin.getConfig().getDouble(path + "z"),
                    (float) plugin.getConfig().getDouble(path + "yaw"),
                    (float) plugin.getConfig().getDouble(path + "pitch"));
        }
        return spawnWorld.getSpawnLocation();
    }

    private World destinationWorld() {
        World preferred = plugin.worldProfiles() == null ? null : plugin.worldProfiles().roleWorld("ffa");
        if (preferred != null && isFfaWorld(preferred)) return preferred;
        for (String name : plugin.getConfig().getStringList("ffa.worlds")) {
            World world = Bukkit.getWorld(name);
            if (world != null && isFfaWorld(world)) return world;
        }
        return null;
    }

    private void refreshModeItems(Player player) {
        if (plugin.selector() != null) Bukkit.getScheduler().runTaskLater(plugin,
                () -> { if (player.isOnline()) plugin.selector().ensureForWorld(player); }, 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        // Before any external world change, put the player's Practice profile back into
        // their inventory so WorldProfileManager (or Multiverse-Inventories) saves the
        // correct profile rather than the temporary FFA inventory.
        if (event.getTo() == null || plugin.worldProfiles() == null) return;
        Player player = event.getPlayer();
        if (isActive(player) && !event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            WorldProfileSnapshot original = returnSnapshots.get(player.getUniqueId());
            if (original != null) original.apply(player);
        }
        // Preserve legacy auto-entry only for a dedicated FFA world. A shared PvP world
        // must not turn every Practice teleport into FFA mode.
        World target = event.getTo().getWorld();
        if (isFfaWorld(target) && !plugin.worldProfiles().isPvpWorld(target)
                && !isFfaWorld(event.getFrom().getWorld())) {
            returnLocations.putIfAbsent(player.getUniqueId(), event.getFrom().clone());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World current = player.getWorld();
        if (isActive(player) && !isFfaWorld(current)) {
            activePlayers.remove(player.getUniqueId());
            returnLocations.remove(player.getUniqueId());
            returnSnapshots.remove(player.getUniqueId());
            if (plugin.protection() != null && plugin.protection().isSafeWorld(current.getName())) {
                plugin.protection().protect(player);
            }
            refreshModeItems(player);
        } else if (!isActive(player) && isFfaWorld(current)
                && plugin.worldProfiles() != null && !plugin.worldProfiles().isPvpWorld(current)) {
            // Backwards compatibility for installations that still have a dedicated FFA world.
            activePlayers.add(player.getUniqueId());
            returnLocations.putIfAbsent(player.getUniqueId(), event.getFrom().getSpawnLocation());
            if (plugin.queues() != null) plugin.queues().leave(player);
            if (plugin.protection() != null) plugin.protection().release(player);
            refreshModeItems(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isFfaWorld(player.getWorld()) && plugin.worldProfiles() != null
                && !plugin.worldProfiles().isPvpWorld(player.getWorld())) {
            activePlayers.add(player.getUniqueId());
            if (plugin.lobby() != null) returnLocations.putIfAbsent(player.getUniqueId(), plugin.lobby().location());
            if (plugin.protection() != null) plugin.protection().release(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!isActive(event.getEntity())) return;
        if (plugin.getConfig().getBoolean("ffa.keep-inventory-on-death", true)) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!isActive(event.getPlayer())) return;
        World world = event.getPlayer().getWorld();
        event.setRespawnLocation(spawnLocation(world));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline() && isActive(event.getPlayer()) && plugin.protection() != null) {
                plugin.protection().release(event.getPlayer());
            }
            refreshModeItems(event.getPlayer());
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        WorldProfileSnapshot returnState = returnSnapshots.get(uuid);
        if (isActive(player) && returnState != null) returnState.apply(player);
        activePlayers.remove(uuid);
        returnLocations.remove(uuid);
        returnSnapshots.remove(uuid);
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
