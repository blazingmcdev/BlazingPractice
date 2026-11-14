package net.blazingpractice.npc;

import net.blazingpractice.BlazingPractice;
import net.blazingpractice.world.PracticeSelector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Persistent, lightweight lobby NPCs for the four server destinations/actions. */
public final class LobbyNpcService implements Listener {
    private static final List<String> IDS = List.of("survival", "practice", "discord", "hosting");
    private final BlazingPractice plugin;
    private final NamespacedKey npcKey;

    public LobbyNpcService(BlazingPractice plugin) {
        this.plugin = plugin;
        this.npcKey = new NamespacedKey(plugin, "lobby_npc_id");
    }

    public void start() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (String id : IDS) ensureNpc(id);
        }, 20L);
    }

    public boolean create(Player creator, String rawId) {
        String id = normalize(rawId);
        if (!IDS.contains(id)) return false;
        removeEntity(id);
        Location location = creator.getLocation().clone();
        String path = "npcs.entries." + id + ".";
        plugin.getConfig().set(path + "world", location.getWorld().getName());
        plugin.getConfig().set(path + "x", location.getX());
        plugin.getConfig().set(path + "y", location.getY());
        plugin.getConfig().set(path + "z", location.getZ());
        plugin.getConfig().set(path + "yaw", location.getYaw());
        plugin.getConfig().set(path + "pitch", location.getPitch());
        plugin.saveConfig();
        spawn(id, location);
        creator.sendMessage(color("&aCreated the &f" + id.toUpperCase(Locale.ROOT) + " &aNPC here."));
        return true;
    }

    public boolean remove(String rawId) {
        String id = normalize(rawId);
        if (!IDS.contains(id)) return false;
        removeEntity(id);
        plugin.getConfig().set("npcs.entries." + id, null);
        plugin.saveConfig();
        return true;
    }

    public List<String> list() {
        List<String> result = new ArrayList<>();
        for (String id : IDS) {
            String world = plugin.getConfig().getString("npcs.entries." + id + ".world", "");
            result.add(id + (world.isBlank() ? " (not placed)" : " @ " + world));
        }
        return result;
    }

    public boolean setUrl(String target, String url) {
        String id = normalize(target);
        if (!id.equals("discord") && !id.equals("hosting")) return false;
        if (!isHttpUrl(url)) return false;
        if (plugin.selector() != null && !plugin.selector().setDestinationUrl(id, url)) return false;
        plugin.getConfig().set("npcs.urls." + id, url);
        plugin.saveConfig();
        return true;
    }

    private void ensureNpc(String id) {
        String path = "npcs.entries." + id + ".";
        String worldName = plugin.getConfig().getString(path + "world", "");
        if (worldName.isBlank() || !plugin.getConfig().contains(path + "x")) return;
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Lobby NPC '" + id + "' waits for world '" + worldName + "' to load.");
            return;
        }
        Location location = new Location(world,
                plugin.getConfig().getDouble(path + "x"), plugin.getConfig().getDouble(path + "y"),
                plugin.getConfig().getDouble(path + "z"), (float) plugin.getConfig().getDouble(path + "yaw"),
                (float) plugin.getConfig().getDouble(path + "pitch"));
        if (findExisting(id, location) == null) spawn(id, location);
    }

    private Villager findExisting(String id, Location location) {
        if (location.getWorld() == null) return null;
        for (Entity entity : location.getChunk().getEntities()) {
            if (entity instanceof Villager villager
                    && id.equals(villager.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING))) {
                return villager;
            }
        }
        return null;
    }

    private void spawn(String id, Location location) {
        if (location.getWorld() == null) return;
        location.getChunk().load();
        Villager villager = location.getWorld().spawn(location, Villager.class, npc -> {
            npc.setAI(false);
            npc.setInvulnerable(true);
            npc.setSilent(true);
            npc.setPersistent(true);
            npc.setRemoveWhenFarAway(false);
            npc.setCanPickupItems(false);
            npc.setAdult();
            npc.setProfession(switch (id) {
                case "survival" -> Villager.Profession.FARMER;
                case "practice" -> Villager.Profession.ARMORER;
                case "discord" -> Villager.Profession.LIBRARIAN;
                default -> Villager.Profession.TOOLSMITH;
            });
            String name = plugin.getConfig().getString("npcs.display-names." + id, defaultName(id));
            npc.setCustomName(ChatColor.translateAlternateColorCodes('&', name));
            npc.setCustomNameVisible(true);
            npc.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, id);
        });
        plugin.getConfig().set("npcs.entries." + id + ".uuid", villager.getUniqueId().toString());
        plugin.saveConfig();
    }

    private void removeEntity(String id) {
        String path = "npcs.entries." + id + ".";
        String worldName = plugin.getConfig().getString(path + "world", "");
        World configuredWorld = worldName.isBlank() ? null : Bukkit.getWorld(worldName);
        if (configuredWorld != null && plugin.getConfig().contains(path + "x")) {
            int chunkX = ((int) Math.floor(plugin.getConfig().getDouble(path + "x"))) >> 4;
            int chunkZ = ((int) Math.floor(plugin.getConfig().getDouble(path + "z"))) >> 4;
            configuredWorld.getChunkAt(chunkX, chunkZ).load();
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager villager
                        && id.equals(villager.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING))) {
                    villager.remove();
                }
            }
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        for (String id : IDS) ensureNpc(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpcClick(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Villager villager)) return;
        String id = villager.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        if (id == null || !IDS.contains(id)) return;
        event.setCancelled(true);
        performAction(event.getPlayer(), id);
    }

    /** Executes the same editable selector/selector.yml action from both an NPC and the wooden-axe GUI. */
    public void performAction(Player player, String rawId) {
        if (player == null || !player.isOnline()) return;
        String id = normalize(rawId);
        if (plugin.selector() != null && plugin.selector().performDestinationAction(player, id)) return;
        switch (id) {
            case "survival" -> joinSurvival(player);
            case "practice" -> openModes(player);
            case "discord" -> openUrl(player, "discord");
            case "hosting" -> openUrl(player, "hosting");
            default -> player.sendMessage(color("&cUnknown server destination."));
        }
    }

    private void joinSurvival(Player player) {
        List<String> survivalWorlds = plugin.worldProfiles().configuredWorldNames("survival");
        World world = survivalWorlds.stream().map(Bukkit::getWorld).filter(w -> w != null).findFirst().orElse(null);
        if (world == null) {
            player.sendMessage(color("&cThe Survival world is not loaded. Configure it with /bp world set survival."));
            return;
        }
        player.teleport(world.getSpawnLocation());
        player.sendMessage(color("&aJoining Survival..."));
    }

    private void openModes(Player player) {
        if (plugin.lobby() != null && !plugin.lobby().isLobbyWorld(player.getWorld())) {
            if (!plugin.lobby().teleport(player)) {
                player.sendMessage(color("&cSet the BlazingPractice lobby first with /blazingpractice lobby set."));
                return;
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> openModeMenu(player), 6L);
        } else {
            openModeMenu(player);
        }
    }

    private void openModeMenu(Player player) {
        PracticeSelector selector = plugin.selector();
        if (selector != null) selector.openLobbyModeMenu(player);
    }

    private void openUrl(Player player, String id) {
        String url = plugin.getConfig().getString("npcs.urls." + id, "");
        if (!isHttpUrl(url)) {
            player.sendMessage(color("&cThe " + id + " link has not been configured by an administrator."));
            return;
        }
        player.sendMessage(Component.text("[BlazingPractice] ", NamedTextColor.GREEN)
                .append(Component.text("Click here to open " + id + ": " + url, NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.openUrl(url))));
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !value.toUpperCase(Locale.ROOT).contains("YOUR_INVITE");
        } catch (Exception ignored) {
            return false;
        }
    }

    private String defaultName(String id) {
        return switch (id) {
            case "survival" -> "&aSURVIVAL";
            case "practice" -> "&bPRACTICE & FFA";
            case "discord" -> "&9JOIN DISCORD";
            default -> "&dOUR HOSTING";
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
