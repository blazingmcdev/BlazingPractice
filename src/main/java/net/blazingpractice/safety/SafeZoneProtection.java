package net.blazingpractice.safety;

import net.blazingpractice.BlazingPractice;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Protects players in configured practice selection/lobby worlds and while queued. */
public final class SafeZoneProtection implements Listener {
    private final BlazingPractice plugin;
    private final Set<UUID> protectedPlayers = ConcurrentHashMap.newKeySet();

    public SafeZoneProtection(BlazingPractice plugin) {
        this.plugin = plugin;
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> isSafeWorld(player.getWorld().getName()))
                .forEach(this::protect);
    }

    public void protect(Player player) {
        protectedPlayers.add(player.getUniqueId());
    }

    public void release(Player player) {
        protectedPlayers.remove(player.getUniqueId());
    }

    public boolean isProtected(Player player) {
        return protectedPlayers.contains(player.getUniqueId());
    }

    public boolean isSafeWorld(String worldName) {
        if (!plugin.getConfig().getBoolean("protection.enabled", true)) return false;
        // In the shared PvP world, players start protected. Explicit FFA join releases
        // that player's protection; queued players and Practice staging remain protected.
        World world = Bukkit.getWorld(worldName);
        if (plugin.worldProfiles() != null) {
            if (plugin.worldProfiles().isSurvivalWorld(world)) return false;
            if (plugin.worldProfiles().isLobbyWorld(world) || plugin.worldProfiles().isPvpWorld(world)) return true;
            if (plugin.worldProfiles().isFfaWorld(world)) return false;
            if (plugin.worldProfiles().isPracticeWorld(world)) return true;
        }
        if (plugin.getConfig().getStringList("ffa.worlds").stream().anyMatch(worldName::equalsIgnoreCase)
                || plugin.getConfig().getString("worlds.ffa", "").equalsIgnoreCase(worldName)) return false;
        if (plugin.getConfig().getString("worlds.lobby", "").equalsIgnoreCase(worldName)
                || plugin.getConfig().getString("worlds.practice", "").equalsIgnoreCase(worldName)) return true;
        return plugin.getConfig().getStringList("protection.safe-worlds").stream()
                .anyMatch(worldName::equalsIgnoreCase);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isSafeWorld(player.getWorld().getName())) protect(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        // Match state is installed before the arena teleport. Never re-protect a live fighter.
        if (plugin.duels() != null && plugin.duels().isInMatch(player)) {
            release(player);
        } else if (plugin.ffa() != null && plugin.ffa().isActive(player)) {
            release(player);
        } else if ((plugin.queues() != null && plugin.queues().isQueued(player))
                || isSafeWorld(player.getWorld().getName())) {
            protect(player);
        } else {
            release(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isProtected(player)) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                player.teleport(player.getWorld().getSpawnLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onModeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || plugin.worldProfiles() == null || plugin.ffa() == null) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player direct) attacker = direct;
        else if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player shooterPlayer) attacker = shooterPlayer;
        }
        if (attacker == null) return;
        if (!plugin.worldProfiles().isPvpWorld(victim.getWorld())
                || !plugin.worldProfiles().isPvpWorld(attacker.getWorld())) return;
        if (!victim.getWorld().getUID().equals(attacker.getWorld().getUID())) return;
        // FFA combat is isolated from Practice staging and duel participants, even though
        // both modes live in the same Minecraft world folder.
        if (plugin.ffa().isActive(attacker) != plugin.ffa().isActive(victim)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isProtected(player)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        protectedPlayers.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        protectedPlayers.clear();
    }
}
