package net.blazingpractice;

import net.blazingpractice.command.CommandRegistry;
import net.blazingpractice.arena.ArenaManager;
import net.blazingpractice.match.DuelService;
import net.blazingpractice.queue.QueueService;
import net.blazingpractice.party.PartyService;
import net.blazingpractice.command.CommandRouter;
import net.blazingpractice.storage.DatabaseManager;
import net.blazingpractice.update.UpdateChecker;
import net.blazingpractice.world.PracticeSelector;
import net.blazingpractice.world.WorldProfileManager;
import net.blazingpractice.safety.SafeZoneProtection;
import net.blazingpractice.lobby.PracticeLobby;
import net.blazingpractice.ffa.FfaService;
import net.blazingpractice.ladder.LadderRegistry;
import net.blazingpractice.npc.LobbyNpcService;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.concurrent.CompletionException;
import java.util.logging.Level;

/**
 * BlazingPractice entry point. This is the first source milestone, not the completed
 * ZonePracticePro feature port.
 */
public final class BlazingPractice extends JavaPlugin {
    private DatabaseManager databaseManager;
    private WorldProfileManager worldProfileManager;
    private PracticeSelector practiceSelector;
    private UpdateChecker updateChecker;
    private CommandRouter commandRouter;
    private ArenaManager arenaManager;
    private DuelService duelService;
    private QueueService queueService;
    private PartyService partyService;
    private SafeZoneProtection safeZoneProtection;
    private PracticeLobby practiceLobby;
    private FfaService ffaService;
    private LobbyNpcService lobbyNpcService;
    private LadderRegistry ladderRegistry;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.databaseManager = new DatabaseManager(this);
        this.commandRouter = new CommandRouter(this);
        CommandRegistry.register(this, commandRouter);

        try {
            // One-time schema bootstrap at startup. Runtime reads/writes use the DB executor.
            databaseManager.initialize().join();
        } catch (CompletionException exception) {
            getLogger().log(Level.SEVERE, "Could not initialize the SQLite database. Disabling plugin.", exception.getCause());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.safeZoneProtection = new SafeZoneProtection(this);
        getServer().getPluginManager().registerEvents(safeZoneProtection, this);
        this.practiceLobby = new PracticeLobby(this);

        this.ladderRegistry = new LadderRegistry(this);
        this.arenaManager = new ArenaManager(this);
        if (!arenaManager.worldGuardStatus().equals("enabled")) {
            getLogger().warning("WorldGuard is not enabled. Dedicated duel arena creation/validation is unavailable until WorldEdit and WorldGuard are installed.");
        } else {
            getLogger().info("WorldGuard integration enabled for dedicated per-ladder duel arenas.");
        }
        this.duelService = new DuelService(this, arenaManager);
        getServer().getPluginManager().registerEvents(duelService, this);
        this.queueService = new QueueService(this);
        getServer().getPluginManager().registerEvents(queueService, this);
        this.queueService.start();
        this.partyService = new PartyService(getConfig().getInt("parties.maximum-size", 8), getConfig().getInt("parties.invite-expiry-seconds", 60));

        this.worldProfileManager = new WorldProfileManager(this, databaseManager);
        this.ffaService = new FfaService(this);
        this.practiceSelector = new PracticeSelector(this, worldProfileManager);
        this.lobbyNpcService = new LobbyNpcService(this);
        getServer().getPluginManager().registerEvents(worldProfileManager, this);
        getServer().getPluginManager().registerEvents(ffaService, this);
        getServer().getPluginManager().registerEvents(practiceSelector, this);
        getServer().getPluginManager().registerEvents(lobbyNpcService, this);
        worldProfileManager.start();
        lobbyNpcService.start();

        this.updateChecker = new UpdateChecker(this);
        commandRouter.setUpdateChecker(updateChecker);
        updateChecker.start();
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onAdminJoin(PlayerJoinEvent event) {
                updateChecker.notifyIfOutdated(event.getPlayer());
            }
        }, this);

        getLogger().info("BlazingPractice " + getPluginMeta().getVersion() + " enabled.");
        getLogger().info("Foundation milestone loaded. Legacy command names are registered; unported handlers are explicitly marked as pending.");
    }

    @Override
    public void onDisable() {
        if (queueService != null) queueService.stop();
        if (duelService != null) duelService.shutdown();
        if (safeZoneProtection != null) safeZoneProtection.shutdown();
        if (worldProfileManager != null) {
            worldProfileManager.saveOnlinePlayers();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("BlazingPractice disabled.");
    }

    public DatabaseManager database() {
        return databaseManager;
    }

    public WorldProfileManager worldProfiles() {
        return worldProfileManager;
    }

    public UpdateChecker updates() {
        return updateChecker;
    }

    public ArenaManager arenas() { return arenaManager; }

    public DuelService duels() { return duelService; }

    public QueueService queues() { return queueService; }

    public PartyService parties() { return partyService; }

    public SafeZoneProtection protection() { return safeZoneProtection; }

    public PracticeLobby lobby() { return practiceLobby; }

    public PracticeSelector selector() { return practiceSelector; }

    public FfaService ffa() { return ffaService; }

    public LobbyNpcService npcs() { return lobbyNpcService; }

    public LadderRegistry ladders() { return ladderRegistry; }
}
