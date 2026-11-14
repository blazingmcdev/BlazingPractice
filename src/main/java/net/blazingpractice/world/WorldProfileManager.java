package net.blazingpractice.world;

import net.blazingpractice.BlazingPractice;
import net.blazingpractice.storage.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Per-world-group inventory/profile fallback. When Multiverse-Inventories is active,
 * this component deliberately does not swap inventory/state to avoid competing backends.
 */
public final class WorldProfileManager implements Listener {
    private final BlazingPractice plugin;
    private final DatabaseManager database;
    private final Set<UUID> pendingLoads = new HashSet<>();
    private final Map<UUID, Long> generations = new HashMap<>();
    private final AtomicLong generationCounter = new AtomicLong();
    private boolean fallbackEnabled;
    private PracticeSelector selector;

    public WorldProfileManager(BlazingPractice plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void setSelector(PracticeSelector selector) {
        this.selector = selector;
    }

    public void start() {
        boolean requested = plugin.getConfig().getBoolean("world-inventories.enabled", true);
        boolean multiverseInventories = plugin.getServer().getPluginManager().isPluginEnabled("Multiverse-Inventories");
        String mode = plugin.getConfig().getString("world-inventories.mode", "auto");
        // A second inventory swapping backend is unsafe. If MVI is active, let it own profiles.
        this.fallbackEnabled = requested && !multiverseInventories && !"disabled".equalsIgnoreCase(mode);
        if (multiverseInventories) {
            plugin.getLogger().info("Multiverse-Inventories detected. Internal inventory swapping is disabled to prevent conflicts.");
        } else if (fallbackEnabled) {
            plugin.getLogger().info("Internal SQLite world-profile fallback enabled (Multiverse-Core worlds are handled through Bukkit world events).");
        } else {
            plugin.getLogger().info("World inventory separation fallback is disabled by configuration.");
        }
    }

    public String backendName() {
        if (plugin.getServer().getPluginManager().isPluginEnabled("Multiverse-Inventories")) return "Multiverse-Inventories (external)";
        return fallbackEnabled ? "BlazingPractice SQLite fallback" : "disabled";
    }

    public boolean isSurvivalWorld(World world) {
        if (world == null) return false;
        return configuredWorldNames("survival").stream()
                .anyMatch(name -> name.equalsIgnoreCase(world.getName()));
    }

    /** Returns the single configured world for a named game role, if it is loaded. */
    public World roleWorld(String role) {
        String name = configuredWorldName(role);
        return name.isBlank() ? null : Bukkit.getWorld(name);
    }

    public String configuredWorldName(String role) {
        String normalized = role.toLowerCase(Locale.ROOT);
        String direct = plugin.getConfig().getString("worlds." + normalized, "");
        if (direct != null && !direct.isBlank()) return direct;
        if (normalized.equals("practice") || normalized.equals("ffa")) {
            String shared = plugin.getConfig().getString("worlds.pvp", "");
            if (shared != null && !shared.isBlank()) return shared;
        }
        var section = plugin.getConfig().getConfigurationSection("world-inventories.groups." + normalized);
        if (section != null) {
            List<String> values = section.getStringList("worlds");
            if (!values.isEmpty()) return values.get(0);
        }
        if (normalized.equals("ffa")) return plugin.getConfig().getString("selector.destinations.ffa-world", "ffa");
        return "";
    }

    public List<String> configuredWorldNames(String role) {
        String normalized = role.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        if (normalized.equals("survival")) {
            names.addAll(plugin.getConfig().getStringList("worlds.survival"));
        } else {
            String direct = plugin.getConfig().getString("worlds." + normalized, "");
            if (direct != null && !direct.isBlank()) names.add(direct);
            if (names.isEmpty() && (normalized.equals("practice") || normalized.equals("ffa"))) {
                String shared = plugin.getConfig().getString("worlds.pvp", "");
                if (shared != null && !shared.isBlank()) names.add(shared);
            }
        }
        if (names.isEmpty()) {
            var section = plugin.getConfig().getConfigurationSection("world-inventories.groups." + normalized);
            if (section != null) names.addAll(section.getStringList("worlds"));
        }
        if (normalized.equals("ffa") && names.isEmpty()) names.addAll(plugin.getConfig().getStringList("ffa.worlds"));
        if (normalized.equals("practice") && names.isEmpty()) names.addAll(plugin.getConfig().getStringList("selector.practice-worlds"));
        if (normalized.equals("pvp") && names.isEmpty()) {
            String shared = plugin.getConfig().getString("worlds.pvp", "");
            if (shared != null && !shared.isBlank()) names.add(shared);
        }
        if (normalized.equals("lobby")) {
            String savedLobbyWorld = plugin.getConfig().getString("lobby.location.world", "");
            if (savedLobbyWorld != null && !savedLobbyWorld.isBlank()
                    && names.stream().noneMatch(name -> name.equalsIgnoreCase(savedLobbyWorld))) names.add(savedLobbyWorld);
        }
        return List.copyOf(names);
    }

    /** Assigns one dedicated world to both Practice and FFA without creating duplicate inventory profiles. */
    public boolean setPvpWorld(World world) {
        if (world == null || isSurvivalWorld(world) || isLobbyWorld(world)) return false;
        String worldName = world.getName();
        String oldPracticeSpawnWorld = plugin.getConfig().getString("practice.spawn.world", "");
        String oldFfaSpawnWorld = plugin.getConfig().getString("ffa.spawn.world", "");
        if (!oldPracticeSpawnWorld.isBlank() && !oldPracticeSpawnWorld.equalsIgnoreCase(worldName)) {
            plugin.getConfig().set("practice.spawn", null);
        }
        if (!oldFfaSpawnWorld.isBlank() && !oldFfaSpawnWorld.equalsIgnoreCase(worldName)) {
            plugin.getConfig().set("ffa.spawn", null);
        }
        plugin.getConfig().set("worlds.pvp", worldName);
        plugin.getConfig().set("worlds.practice", worldName);
        plugin.getConfig().set("worlds.ffa", worldName);
        plugin.getConfig().set("selector.practice-worlds", List.of(worldName));
        plugin.getConfig().set("selector.allowed-worlds", List.of(worldName));
        plugin.getConfig().set("selector.destinations.ffa-world", worldName);
        plugin.getConfig().set("ffa.worlds", List.of(worldName));

        var groups = plugin.getConfig().getConfigurationSection("world-inventories.groups");
        if (groups != null) {
            for (String group : groups.getKeys(false)) {
                if (!group.equalsIgnoreCase("survival") && !group.equalsIgnoreCase("lobby")
                        && !group.equalsIgnoreCase("practice") && !group.equalsIgnoreCase("ffa")) {
                    List<String> worlds = new ArrayList<>(groups.getStringList(group + ".worlds"));
                    worlds.removeIf(name -> name.equalsIgnoreCase(worldName));
                    groups.set(group + ".worlds", worlds);
                }
            }
        }
        plugin.getConfig().set("world-inventories.groups.pvp.worlds", List.of(worldName));
        // Keep legacy Practice/FFA group memberships for this world if it used one before
        // the upgrade. profileKey() below reuses that stored snapshot instead of stranding it.
        plugin.saveConfig();
        return true;
    }

    /** Assigns a legacy single-mode role. Use role=pvp for the recommended shared Practice+FFA world. */
    public boolean setWorldRole(String role, World world) {
        if (world == null) return false;
        String normalized = role.toLowerCase(Locale.ROOT);
        if (normalized.equals("pvp")) return setPvpWorld(world);
        if (!Set.of("lobby", "practice", "ffa", "survival").contains(normalized)) return false;
        String worldName = world.getName();
        if (!normalized.equals("survival") && isSurvivalWorld(world)) return false;
        for (String other : List.of("lobby", "practice", "ffa", "survival", "pvp")) {
            if (other.equals(normalized) || (normalized.equals("practice") && other.equals("ffa"))
                    || (normalized.equals("ffa") && other.equals("practice"))) continue;
            if (configuredWorldNames(other).stream().anyMatch(name -> name.equalsIgnoreCase(worldName))) return false;
        }
        var groups = plugin.getConfig().getConfigurationSection("world-inventories.groups");
        if (groups != null) {
            for (String group : groups.getKeys(false)) {
                if (!group.equalsIgnoreCase("survival")) {
                    List<String> names = new ArrayList<>(groups.getStringList(group + ".worlds"));
                    names.removeIf(name -> name.equalsIgnoreCase(worldName));
                    groups.set(group + ".worlds", names);
                }
            }
        }
        if (normalized.equals("lobby")) {
            String oldLocationWorld = plugin.getConfig().getString("lobby.location.world", "");
            if (!oldLocationWorld.isBlank() && !oldLocationWorld.equalsIgnoreCase(worldName)) plugin.getConfig().set("lobby.location", null);
        }
        if (normalized.equals("survival")) {
            List<String> names = new ArrayList<>(configuredWorldNames("survival"));
            if (names.stream().noneMatch(name -> name.equalsIgnoreCase(worldName))) names.add(worldName);
            plugin.getConfig().set("worlds.survival", names);
            List<String> groupNames = new ArrayList<>(plugin.getConfig().getStringList("world-inventories.groups.survival.worlds"));
            if (groupNames.stream().noneMatch(name -> name.equalsIgnoreCase(worldName))) groupNames.add(worldName);
            plugin.getConfig().set("world-inventories.groups.survival.worlds", groupNames);
        } else {
            plugin.getConfig().set("worlds." + normalized, worldName);
            plugin.getConfig().set("world-inventories.groups." + normalized + ".worlds", List.of(worldName));
            if (normalized.equals("ffa")) {
                plugin.getConfig().set("ffa.worlds", List.of(worldName));
                plugin.getConfig().set("selector.destinations.ffa-world", worldName);
            }
            if (normalized.equals("practice")) {
                plugin.getConfig().set("selector.practice-worlds", List.of(worldName));
                plugin.getConfig().set("selector.allowed-worlds", List.of(worldName));
            }
        }
        plugin.saveConfig();
        return true;
    }

    public boolean isPvpWorld(World world) {
        if (world == null || isSurvivalWorld(world) || isLobbyWorldName(world)) return false;
        return configuredWorldNames("pvp").stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()))
                || (configuredWorldNames("practice").stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()))
                && configuredWorldNames("ffa").stream().anyMatch(name -> name.equalsIgnoreCase(world.getName())));
    }

    public boolean isFfaWorld(World world) {
        if (world == null || isSurvivalWorld(world) || isLobbyWorld(world)) return false;
        return configuredWorldNames("ffa").stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()));
    }

    /** Practice world is the duel/queue environment. It may share its world folder with FFA. */
    public boolean isPracticeWorld(World world) {
        if (world == null || isSurvivalWorld(world) || isLobbyWorld(world)) return false;
        return configuredWorldNames("practice").stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()));
    }

    public boolean isLobbyWorld(World world) {
        if (world == null || isSurvivalWorld(world) || isPvpWorld(world) || isPracticeWorldName(world) || isFfaWorldName(world)) return false;
        return configuredWorldNames("lobby").stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()));
    }

    private boolean isLobbyWorldName(World world) {
        return configuredWorldNames("lobby").stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()));
    }

    private boolean isFfaWorldName(World world) {
        return configuredWorldNames("ffa").stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()));
    }

    private boolean isPracticeWorldName(World world) {
        return configuredWorldNames("practice").stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()));
    }

    /** Lobby and shared PvP world receive mode controls; active FFA players receive the leave item. */
    public boolean isSelectorWorld(World world) {
        return isLobbyWorld(world) || isPracticeWorld(world) || isPvpWorld(world);
    }

    public Location practiceSpawnLocation() {
        World world = roleWorld("practice");
        if (world == null) return null;
        String path = "practice.spawn.";
        if (plugin.getConfig().contains(path + "x")) {
            return new Location(world, plugin.getConfig().getDouble(path + "x"),
                    plugin.getConfig().getDouble(path + "y"), plugin.getConfig().getDouble(path + "z"),
                    (float) plugin.getConfig().getDouble(path + "yaw"),
                    (float) plugin.getConfig().getDouble(path + "pitch"));
        }
        return world.getSpawnLocation();
    }

    public boolean setPracticeSpawn(Player player) {
        if (!isPracticeWorld(player.getWorld())) return false;
        Location location = player.getLocation();
        String path = "practice.spawn.";
        plugin.getConfig().set(path + "world", location.getWorld().getName());
        plugin.getConfig().set(path + "x", location.getX());
        plugin.getConfig().set(path + "y", location.getY());
        plugin.getConfig().set(path + "z", location.getZ());
        plugin.getConfig().set(path + "yaw", location.getYaw());
        plugin.getConfig().set(path + "pitch", location.getPitch());
        plugin.saveConfig();
        return true;
    }

    public boolean teleportToPractice(Player player) {
        Location destination = practiceSpawnLocation();
        return destination != null && player.teleport(destination);
    }

    /**
     * Runs a task after BlazingPractice's asynchronous world-profile swap has completed.
     * A short initial delay also gives external inventory providers (for example
     * Multiverse-Inventories) an opportunity to process the world transition.
     */
    public void whenReady(Player player, Runnable task) {
        if (player == null || task == null) return;
        waitForProfile(player, task, 0);
    }

    private void waitForProfile(Player player, Runnable task, int waitedTicks) {
        if (!player.isOnline()) return;
        if (waitedTicks < 5 || pendingLoads.contains(player.getUniqueId())) {
            if (waitedTicks >= 600) {
                player.sendMessage("§cYour world inventory is still loading. The mode switch was cancelled to protect your items.");
                return;
            }
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> waitForProfile(player, task, waitedTicks + 1), 1L);
            return;
        }
        task.run();
    }

    public boolean isProfileLoading(Player player) {
        return player != null && pendingLoads.contains(player.getUniqueId());
    }

    public String profileKey(World world) {
        if (isSurvivalWorld(world)) return "group:survival";
        if (isLobbyWorld(world)) return "group:lobby";
        if (isPvpWorld(world)) return sharedPvpProfileKey(world);
        if (isFfaWorld(world)) return "group:ffa";
        if (isPracticeWorld(world)) return "group:practice";
        String worldName = world.getName();
        var groups = plugin.getConfig().getConfigurationSection("world-inventories.groups");
        if (groups != null) {
            for (String groupName : groups.getKeys(false)) {
                for (String configuredWorld : groups.getStringList(groupName + ".worlds")) {
                    if (configuredWorld.equalsIgnoreCase(worldName)) {
                        return "group:" + groupName.toLowerCase(Locale.ROOT);
                    }
                }
            }
        }
        return "world:" + worldName.toLowerCase(Locale.ROOT);
    }

    private String sharedPvpProfileKey(World world) {
        var groups = plugin.getConfig().getConfigurationSection("world-inventories.groups");
        if (groups != null) {
            String worldName = world.getName();
            for (String legacyGroup : List.of("practice", "ffa")) {
                if (groups.getStringList(legacyGroup + ".worlds").stream()
                        .anyMatch(name -> name.equalsIgnoreCase(worldName))) {
                    return "group:" + legacyGroup;
                }
            }
        }
        return "group:pvp";
    }

    private String primaryProfileKey() {
        String primaryName = plugin.getConfig().getString("world-inventories.primary-profile", "survival");
        return "group:" + primaryName.toLowerCase(Locale.ROOT);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        database.savePlayerName(player.getUniqueId(), player.getName());
        if (!fallbackEnabled) {
            if (selector != null) Bukkit.getScheduler().runTask(plugin, () -> selector.ensureForWorld(player));
            return;
        }
        beginJoinLoad(player);
    }

    private void beginJoinLoad(Player player) {
        UUID uuid = player.getUniqueId();
        String currentKey = profileKey(player.getWorld());
        String primaryKey = primaryProfileKey();
        long generation = generationCounter.incrementAndGet();
        generations.put(uuid, generation);
        pendingLoads.add(uuid);

        // Capture the Bukkit-loaded player.dat state before clearing it. On first join this
        // is the migration source for the configured primary (normally Survival) profile.
        WorldProfileSnapshot initialState = WorldProfileSnapshot.capture(player);
        clearPlayerState(player);

        database.loadWorldProfile(uuid, currentKey).thenCombine(database.loadWorldProfile(uuid, primaryKey),
                (current, primary) -> new LoadedProfiles(current, primary)).whenComplete((loaded, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!isCurrent(player, currentKey, generation)) return;
                    pendingLoads.remove(uuid);
                    if (error != null) {
                        plugin.getLogger().log(Level.SEVERE, "Could not load world profile for " + player.getName(), error);
                        // Never put the vanilla/Survival inventory into a non-primary world on a storage error.
                        if (currentKey.equals(primaryKey)) initialState.apply(player);
                        else WorldProfileSnapshot.empty().apply(player);
                        if (selector != null) selector.ensureForWorld(player);
                        player.sendMessage("§cYour world profile could not be loaded. The destination was kept empty to prevent inventory leakage. Please notify an administrator.");
                        return;
                    }
                    try {
                        if (!currentKey.equals(primaryKey) && loaded.primary.isEmpty()) persist(uuid, primaryKey, initialState);
                        WorldProfileSnapshot currentSnapshot;
                        if (loaded.current.isPresent()) {
                            currentSnapshot = WorldProfileSnapshot.deserialize(loaded.current.get());
                        } else if (currentKey.equals(primaryKey)) {
                            currentSnapshot = initialState;
                            persist(uuid, currentKey, currentSnapshot);
                        } else {
                            currentSnapshot = WorldProfileSnapshot.empty();
                        }
                        currentSnapshot.apply(player);
                        if (selector != null) selector.ensureForWorld(player);
                    } catch (Exception exception) {
                        plugin.getLogger().log(Level.SEVERE, "Could not deserialize world profile for " + player.getName(), exception);
                        if (currentKey.equals(primaryKey)) initialState.apply(player);
                        else WorldProfileSnapshot.empty().apply(player);
                        if (selector != null) selector.ensureForWorld(player);
                    }
                }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (selector != null) Bukkit.getScheduler().runTask(plugin, () -> selector.ensureForWorld(player));
        if (!fallbackEnabled) return;

        String fromKey = profileKey(event.getFrom());
        String toKey = profileKey(player.getWorld());
        if (fromKey.equals(toKey)) return;

        UUID uuid = player.getUniqueId();
        WorldProfileSnapshot outgoing = WorldProfileSnapshot.capture(player);
        long generation = generationCounter.incrementAndGet();
        generations.put(uuid, generation);
        pendingLoads.add(uuid);
        clearPlayerState(player);

        try {
            byte[] bytes = outgoing.serialize();
            database.saveWorldProfile(uuid, fromKey, bytes)
                    .thenCompose(ignored -> database.loadWorldProfile(uuid, toKey))
                    .whenComplete((stored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!isCurrent(player, toKey, generation)) return;
                        pendingLoads.remove(uuid);
                        if (error != null) {
                            plugin.getLogger().log(Level.SEVERE, "World inventory transition failed for " + player.getName(), error);
                            WorldProfileSnapshot.empty().apply(player);
                            if (selector != null) selector.ensureForWorld(player);
                            player.sendMessage("§cWorld inventory loading failed. Your previous world inventory was saved; this world was opened empty to prevent item duplication.");
                            return;
                        }
                        try {
                            WorldProfileSnapshot incoming = stored.isPresent()
                                    ? WorldProfileSnapshot.deserialize(stored.get())
                                    : WorldProfileSnapshot.empty();
                            incoming.apply(player);
                            if (selector != null) selector.ensureForWorld(player);
                        } catch (Exception exception) {
                            plugin.getLogger().log(Level.SEVERE, "Could not read destination world profile for " + player.getName(), exception);
                            WorldProfileSnapshot.empty().apply(player);
                            if (selector != null) selector.ensureForWorld(player);
                        }
                    }));
        } catch (IOException exception) {
            pendingLoads.remove(uuid);
            plugin.getLogger().log(Level.SEVERE, "Could not serialize outgoing profile for " + player.getName(), exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!fallbackEnabled || pendingLoads.contains(player.getUniqueId())) return;
        try {
            persist(player.getUniqueId(), profileKey(player.getWorld()), WorldProfileSnapshot.capture(player));
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save profile on quit for " + player.getName(), exception);
        }
    }

    public void saveOnlinePlayers() {
        if (!fallbackEnabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (pendingLoads.contains(player.getUniqueId())) continue;
            try {
                WorldProfileSnapshot snapshot = plugin.ffa() == null
                        ? WorldProfileSnapshot.capture(player)
                        : plugin.ffa().snapshotForWorldSave(player);
                persist(player.getUniqueId(), profileKey(player.getWorld()), snapshot);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not snapshot " + player.getName() + " during shutdown", exception);
            }
        }
    }

    private void persist(UUID uuid, String key, WorldProfileSnapshot snapshot) {
        try {
            database.saveWorldProfile(uuid, key, snapshot.serialize());
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not serialize profile " + key + " for " + uuid, exception);
        }
    }

    private boolean isCurrent(Player player, String expectedProfile, long generation) {
        if (!player.isOnline()) return false;
        Long actual = generations.get(player.getUniqueId());
        return actual != null && actual == generation && profileKey(player.getWorld()).equals(expectedProfile);
    }

    private void clearPlayerState(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.getEnderChest().clear();
        player.setLevel(0);
        player.setExp(0.0f);
        player.setTotalExperience(0);
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setExhaustion(0.0f);
        for (var effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void lockInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && pendingLoads.contains(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void lockInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && pendingLoads.contains(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void lockDrop(PlayerDropItemEvent event) {
        if (pendingLoads.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void lockPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && pendingLoads.contains(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void lockSwap(PlayerSwapHandItemsEvent event) {
        if (pendingLoads.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void lockHeldSlot(PlayerItemHeldEvent event) {
        if (pendingLoads.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void lockCrossWorldTeleportWhileLoading(PlayerTeleportEvent event) {
        if (event.getTo() != null && event.getFrom().getWorld() != event.getTo().getWorld()
                && pendingLoads.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private record LoadedProfiles(Optional<byte[]> current, Optional<byte[]> primary) { }
}
