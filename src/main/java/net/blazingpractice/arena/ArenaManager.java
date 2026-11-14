package net.blazingpractice.arena;

import net.blazingpractice.BlazingPractice;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Persistent per-ladder arena registry with runtime-only reservations.
 * All Bukkit/WorldGuard access is expected to happen on the server thread.
 */
public final class ArenaManager {
    private final BlazingPractice plugin;
    private final File file;
    private WorldGuardBridge worldGuard;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<String, UUID> reservations = new HashMap<>();
    private YamlConfiguration data;

    public ArenaManager(BlazingPractice plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        if (!file.exists()) {
            try {
                plugin.saveResource("arenas.yml", false);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Bundled arenas.yml was missing; creating it with default pool limits.");
            }
        }
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().severe("Could not create arenas.yml: " + exception.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
        seedPoolDefaults();
        arenas.clear();

        ConfigurationSection root = data.getConfigurationSection("arenas");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                String path = "arenas." + key;
                World world = Bukkit.getWorld(data.getString(path + ".world", ""));
                String ladder = data.getString(path + ".ladder");
                if (ladder == null || ladder.isBlank()) {
                    ladder = data.getString(path + ".type", "unassigned");
                }
                // Old generic DUEL arenas are not assigned to a ladder automatically:
                // doing so could put an Axe/Crystal match into the wrong arena.
                if (ladder.equalsIgnoreCase("DUEL")) ladder = "unassigned";

                String region = data.getString(path + ".region",
                        data.getString(path + ".region-id", ""));
                Location spawnOne = readLocation(path + ".spawn1");
                if (spawnOne == null) spawnOne = readLocation(path + ".spawn-one");
                Location spawnTwo = readLocation(path + ".spawn2");
                if (spawnTwo == null) spawnTwo = readLocation(path + ".spawn-two");

                Arena arena = new Arena(key, normalize(ladder), region, world,
                        readLocation(path + ".corner-one"), readLocation(path + ".corner-two"),
                        spawnOne, spawnTwo,
                        data.getBoolean(path + ".enabled", false) && !ladder.equalsIgnoreCase("unassigned"));
                arenas.put(normalize(key), arena);
            }
        }
        save();
    }

    private void seedPoolDefaults() {
        Map<String, int[]> defaults = defaultPoolLimits();
        boolean changed = false;
        for (Map.Entry<String, int[]> entry : defaults.entrySet()) {
            String path = "arena-pools." + entry.getKey();
            if (!data.contains(path + ".min-arenas")) {
                data.set(path + ".min-arenas", entry.getValue()[0]);
                changed = true;
            }
            if (!data.contains(path + ".max-arenas")) {
                data.set(path + ".max-arenas", entry.getValue()[1]);
                changed = true;
            }
        }
        if (!data.contains("arena-settings.minimum-spawn-distance")) {
            data.set("arena-settings.minimum-spawn-distance", 3.0);
            changed = true;
        }
        if (changed) save();
    }

    private Map<String, int[]> defaultPoolLimits() {
        Map<String, int[]> pools = new LinkedHashMap<>();
        // High-demand modes can grow to eight maps. Other ladders start with smaller pools.
        Set<String> popular = Set.of("sword", "axe", "crystal", "mace", "netheritepotion", "diamondpotion");
        Set<String> medium = Set.of("boxing", "sumo", "builduhc", "gapple", "spearmace", "spearelytra");
        for (String ladder : plugin.ladders() == null ? List.<String>of() :
                plugin.ladders().all().stream().map(value -> value.id()).toList()) {
            if (popular.contains(ladder)) pools.put(ladder, new int[]{5, 8});
            else if (medium.contains(ladder)) pools.put(ladder, new int[]{5, 5});
            else pools.put(ladder, new int[]{2, 3});
        }
        return pools;
    }

    public OperationResult create(String name, String ladderId, String regionId, World world) {
        String key = normalize(name);
        String ladder = normalize(ladderId);
        String region = regionId == null ? "" : regionId.trim();

        if (key.isBlank() || ladder.isBlank() || region.isBlank() || world == null) {
            return fail("Arena ID, ladder, region and a valid world are required.");
        }
        if (arenas.containsKey(key)) return fail("An arena with that ID already exists.");
        if (plugin.ladders() == null || plugin.ladders().find(ladder).isEmpty()) {
            return fail("Unknown ladder '" + ladder + "'. Check /ladder list.");
        }
        ensurePoolDefaults(ladder);
        if (plugin.worldProfiles() == null || !plugin.worldProfiles().isPracticeWorld(world)) {
            return fail("Duel arenas must be created inside the configured shared Practice/PvP world.");
        }
        WorldGuardBridge bridge = worldGuard();
        if (bridge == null) {
            return fail("WorldGuard is not installed or enabled. Install WorldEdit and WorldGuard, then restart.");
        }
        if (!bridge.regionExists(world, region)) {
            return fail("WorldGuard region '" + region + "' was not found in world '" + world.getName() + "'.");
        }
        if (regionAlreadyRegistered(world, region)) {
            return fail("That WorldGuard region is already assigned to another arena.");
        }
        int max = maxArenas(ladder);
        long registered = arenas.values().stream().filter(a -> a.ladder().equalsIgnoreCase(ladder)).count();
        if (registered >= max) {
            return fail("The " + ladder + " pool reached its max-arenas limit (" + max + "). Change arenas.yml to increase it.");
        }

        String path = "arenas." + key;
        data.set(path + ".ladder", ladder);
        data.set(path + ".region", region);
        data.set(path + ".world", world.getName());
        data.set(path + ".enabled", false);
        save();
        reload();
        return ok("Created arena '" + key + "' for " + ladder + " using WorldGuard region '" + region
                + "'. Set both spawns, then enable it.");
    }

    private void ensurePoolDefaults(String ladder) {
        String path = "arena-pools." + normalize(ladder);
        boolean changed = false;
        if (!data.contains(path + ".min-arenas")) {
            data.set(path + ".min-arenas", 2);
            changed = true;
        }
        if (!data.contains(path + ".max-arenas")) {
            data.set(path + ".max-arenas", 5);
            changed = true;
        }
        if (changed) save();
    }

    public OperationResult delete(String name) {
        String key = normalize(name);
        if (!arenas.containsKey(key)) return fail("Arena not found.");
        if (reservations.containsKey(key)) return fail("That arena is currently busy and cannot be deleted.");
        data.set("arenas." + key, null);
        save();
        reload();
        return ok("Arena '" + key + "' deleted.");
    }

    public OperationResult setPoint(String name, String point, Location location) {
        String key = normalize(name);
        Arena arena = arenas.get(key);
        if (arena == null) return fail("Arena not found.");
        if (reservations.containsKey(key)) return fail("That arena is currently busy; wait for its match to finish.");
        if (location == null || location.getWorld() == null || arena.world() == null
                || !arena.world().getUID().equals(location.getWorld().getUID())) {
            return fail("Stand inside the arena's configured world before setting a spawn.");
        }
        if (plugin.worldProfiles() == null || !plugin.worldProfiles().isPracticeWorld(location.getWorld())) {
            return fail("Duel spawns must be inside the shared Practice/PvP world.");
        }
        WorldGuardBridge bridge = worldGuard();
        if (bridge == null) return fail("WorldGuard is not installed or enabled.");
        if (!bridge.regionExists(arena.world(), arena.region())) {
            return fail("The configured WorldGuard region no longer exists. Re-create or fix the region first.");
        }
        if (point.equalsIgnoreCase("spawn-one") || point.equalsIgnoreCase("spawn-two")) {
            if (!bridge.contains(arena.world(), arena.region(), location)) {
                return fail("Your location is outside WorldGuard region '" + arena.region() + "'.");
            }
            Location other = point.equalsIgnoreCase("spawn-one") ? arena.spawnTwo() : arena.spawnOne();
            if (other != null) {
                if (other.getWorld() == null || !other.getWorld().getUID().equals(location.getWorld().getUID())) {
                    return fail("The other spawn is not in the configured arena world. Reconfigure it first.");
                }
                if (other.distanceSquared(location) <
                        Math.pow(data.getDouble("arena-settings.minimum-spawn-distance", 3.0), 2.0)) {
                    return fail("The two player spawns must be at least "
                            + data.getDouble("arena-settings.minimum-spawn-distance", 3.0) + " blocks apart.");
                }
            }
        } else if (!point.equalsIgnoreCase("corner-one") && !point.equalsIgnoreCase("corner-two")) {
            return fail("Unknown arena point.");
        }

        String storagePoint = switch (point.toLowerCase(Locale.ROOT)) {
            case "spawn-one" -> "spawn1";
            case "spawn-two" -> "spawn2";
            default -> point.toLowerCase(Locale.ROOT);
        };
        String path = "arenas." + key;
        writeLocation(path + "." + storagePoint, location);
        save();
        reload();
        return ok("Saved " + storagePoint + " for arena '" + key + "'.");
    }

    public OperationResult setEnabled(String name, boolean enabled) {
        String key = normalize(name);
        Arena arena = arenas.get(key);
        if (arena == null) return fail("Arena not found.");
        if (reservations.containsKey(key)) return fail("That arena is currently busy; wait for its match to finish.");
        if (enabled) {
            String problem = configurationError(arena);
            if (problem != null) return fail(problem);
        }
        data.set("arenas." + key + ".enabled", enabled);
        save();
        reload();
        return ok(enabled ? "Arena '" + key + "' enabled." : "Arena '" + key + "' disabled.");
    }

    public Optional<Arena> get(String name) {
        return Optional.ofNullable(arenas.get(normalize(name)));
    }

    public List<Arena> all() {
        return List.copyOf(arenas.values());
    }

    public List<String> worldGuardRegionIds(World world) {
        WorldGuardBridge bridge = worldGuard();
        return bridge == null ? List.of() : bridge.regionIds(world);
    }

    public boolean hasReadyArena(String ladderId) {
        String ladder = normalize(ladderId);
        return arenas.values().stream().anyMatch(arena -> arena.ladder().equals(ladder)
                && validationError(arena) == null);
    }

    public boolean hasFreeArena(String ladderId) {
        String ladder = normalize(ladderId);
        return arenas.values().stream().anyMatch(arena -> arena.ladder().equals(ladder)
                && !reservations.containsKey(normalize(arena.name()))
                && validationError(arena) == null);
    }

    public Optional<ArenaReservation> reserve(String ladderId) {
        String ladder = normalize(ladderId);
        for (Arena arena : arenas.values()) {
            String key = normalize(arena.name());
            if (!arena.ladder().equals(ladder) || reservations.containsKey(key)
                    || validationError(arena) != null) continue;
            UUID token = UUID.randomUUID();
            reservations.put(key, token);
            return Optional.of(new ArenaReservation(arena, token));
        }
        return Optional.empty();
    }

    public boolean release(ArenaReservation reservation) {
        if (reservation == null || reservation.arena() == null || reservation.token() == null) return false;
        String key = normalize(reservation.arena().name());
        UUID owner = reservations.get(key);
        if (!reservation.token().equals(owner)) return false;
        reservations.remove(key);
        return true;
    }

    public void clearReservations() {
        reservations.clear();
    }

    public boolean isBusy(String arenaId) {
        return reservations.containsKey(normalize(arenaId));
    }

    public int busyCount(String ladderId) {
        String ladder = normalize(ladderId);
        return (int) reservations.keySet().stream()
                .map(arenas::get)
                .filter(Objects::nonNull)
                .filter(arena -> arena.ladder().equals(ladder))
                .count();
    }

    public int readyCount(String ladderId) {
        String ladder = normalize(ladderId);
        return (int) arenas.values().stream().filter(arena -> arena.ladder().equals(ladder))
                .filter(arena -> validationError(arena) == null).count();
    }

    public int configuredCount(String ladderId) {
        String ladder = normalize(ladderId);
        return (int) arenas.values().stream().filter(arena -> arena.ladder().equals(ladder)).count();
    }

    public int minArenas(String ladderId) {
        return Math.max(1, data.getInt("arena-pools." + normalize(ladderId) + ".min-arenas", 2));
    }

    public int maxArenas(String ladderId) {
        return Math.max(1, data.getInt("arena-pools." + normalize(ladderId) + ".max-arenas", 5));
    }

    public String validationError(Arena arena) {
        if (arena == null || !arena.enabled()) return "Arena is disabled.";
        return configurationError(arena);
    }

    private String configurationError(Arena arena) {
        if (arena == null) return "Arena is missing.";
        if (arena.world() == null) return "World '" + arena.name() + "' is not loaded.";
        if (arena.ladder() == null || arena.ladder().isBlank() || arena.ladder().equals("unassigned")) {
            return "Arena has no ladder assigned.";
        }
        if (plugin.worldProfiles() == null || !plugin.worldProfiles().isPracticeWorld(arena.world())) {
            return "Arena is not inside the configured Practice/PvP world.";
        }
        if (arena.region() == null || arena.region().isBlank()) return "No WorldGuard region is configured.";
        WorldGuardBridge bridge = worldGuard();
        if (bridge == null) return "WorldGuard is not installed or enabled.";
        if (!bridge.regionExists(arena.world(), arena.region())) return "WorldGuard region '" + arena.region() + "' does not exist.";
        if (arena.spawnOne() == null || arena.spawnTwo() == null) return "Both player spawns must be configured.";
        if (arena.spawnOne().getWorld() == null || arena.spawnTwo().getWorld() == null
                || !arena.world().getUID().equals(arena.spawnOne().getWorld().getUID())
                || !arena.world().getUID().equals(arena.spawnTwo().getWorld().getUID())) {
            return "Both player spawns must be in the arena world.";
        }
        if (!bridge.contains(arena.world(), arena.region(), arena.spawnOne())
                || !bridge.contains(arena.world(), arena.region(), arena.spawnTwo())) {
            return "Both player spawns must be inside WorldGuard region '" + arena.region() + "'.";
        }
        double minDistance = data.getDouble("arena-settings.minimum-spawn-distance", 3.0);
        if (arena.spawnOne().distanceSquared(arena.spawnTwo()) < minDistance * minDistance) {
            return "The two player spawns must be at least " + minDistance + " blocks apart.";
        }
        return null;
    }

    public String worldGuardStatus() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldGuard") ? "enabled" : "missing";
    }

    /** Load the class that links against WorldGuard only after its plugin is confirmed enabled. */
    private WorldGuardBridge worldGuard() {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return null;
        if (worldGuard == null) worldGuard = new WorldGuardBridge();
        return worldGuard;
    }

    private boolean regionAlreadyRegistered(World world, String region) {
        return arenas.values().stream().anyMatch(arena -> arena.world() != null
                && arena.world().getUID().equals(world.getUID())
                && arena.region() != null && arena.region().equalsIgnoreCase(region));
    }

    private Location readLocation(String path) {
        String worldName = data.getString(path + ".world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null || !data.contains(path + ".x")) return null;
        return new Location(world, data.getDouble(path + ".x"), data.getDouble(path + ".y"),
                data.getDouble(path + ".z"), (float) data.getDouble(path + ".yaw"),
                (float) data.getDouble(path + ".pitch"));
    }

    private void writeLocation(String path, Location location) {
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", location.getYaw());
        data.set(path + ".pitch", location.getPitch());
    }

    private OperationResult ok(String message) {
        return new OperationResult(true, "§a" + message);
    }

    private OperationResult fail(String message) {
        return new OperationResult(false, "§c" + message);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save arenas.yml: " + exception.getMessage());
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    public record OperationResult(boolean success, String message) { }
}
