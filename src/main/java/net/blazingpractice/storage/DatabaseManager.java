package net.blazingpractice.storage;

import net.blazingpractice.BlazingPractice;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** SQLite storage. Runtime database operations are serialized on a dedicated worker. */
public final class DatabaseManager {
    private final BlazingPractice plugin;
    private final File databaseFile;
    private final ExecutorService executor;
    private volatile boolean initialized;

    public DatabaseManager(BlazingPractice plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.sqlite-file", "data.db"));
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "BlazingPractice-SQLite");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                File parent = databaseFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IllegalStateException("Could not create database directory: " + parent);
                }
                Class.forName("org.sqlite.JDBC");
                try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA journal_mode=WAL");
                    statement.execute("PRAGMA foreign_keys=ON");
                    statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL)");
                    statement.execute("CREATE TABLE IF NOT EXISTS world_profiles ("
                            + "uuid TEXT NOT NULL, profile_key TEXT NOT NULL, snapshot BLOB NOT NULL, updated_at INTEGER NOT NULL, "
                            + "PRIMARY KEY(uuid, profile_key))");
                    statement.execute("CREATE TABLE IF NOT EXISTS player_profiles ("
                            + "uuid TEXT PRIMARY KEY, last_known_name TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
                    statement.execute("CREATE TABLE IF NOT EXISTS player_ratings ("
                            + "uuid TEXT NOT NULL, ladder TEXT NOT NULL, rating INTEGER NOT NULL DEFAULT 1000, "
                            + "wins INTEGER NOT NULL DEFAULT 0, losses INTEGER NOT NULL DEFAULT 0, streak INTEGER NOT NULL DEFAULT 0, "
                            + "peak_rating INTEGER NOT NULL DEFAULT 1000, updated_at INTEGER NOT NULL, PRIMARY KEY(uuid, ladder))");
                    statement.execute("CREATE TABLE IF NOT EXISTS match_history ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, ladder TEXT NOT NULL, winner_uuid TEXT NOT NULL, "
                            + "loser_uuid TEXT NOT NULL, winner_before INTEGER NOT NULL, loser_before INTEGER NOT NULL, "
                            + "winner_after INTEGER NOT NULL, loser_after INTEGER NOT NULL, played_at INTEGER NOT NULL)");
                    statement.execute("CREATE INDEX IF NOT EXISTS idx_match_history_players ON match_history(winner_uuid, loser_uuid, played_at)");
                    statement.execute("INSERT OR IGNORE INTO schema_migrations(version, applied_at) VALUES (1, strftime('%s','now'))");
                    statement.execute("INSERT OR IGNORE INTO schema_migrations(version, applied_at) VALUES (2, strftime('%s','now'))");
                }
                initialized = true;
            } catch (Exception exception) {
                throw new IllegalStateException("SQLite initialization failed", exception);
            }
        }, executor);
    }

    public CompletableFuture<Optional<byte[]>> loadWorldProfile(UUID uuid, String profileKey) {
        return CompletableFuture.supplyAsync(() -> {
            requireInitialized();
            try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                    "SELECT snapshot FROM world_profiles WHERE uuid = ? AND profile_key = ?")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, profileKey);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(result.getBytes(1)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not load world profile " + profileKey + " for " + uuid, exception);
            }
        }, executor);
    }

    public CompletableFuture<Void> saveWorldProfile(UUID uuid, String profileKey, byte[] snapshot) {
        return CompletableFuture.runAsync(() -> {
            requireInitialized();
            try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO world_profiles(uuid, profile_key, snapshot, updated_at) VALUES(?, ?, ?, ?) "
                            + "ON CONFLICT(uuid, profile_key) DO UPDATE SET snapshot=excluded.snapshot, updated_at=excluded.updated_at")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, profileKey);
                statement.setBytes(3, snapshot);
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not save world profile " + profileKey + " for " + uuid, exception);
            }
        }, executor);
    }

    public CompletableFuture<Void> savePlayerName(UUID uuid, String name) {
        return CompletableFuture.runAsync(() -> {
            requireInitialized();
            long now = System.currentTimeMillis();
            try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO player_profiles(uuid, last_known_name, created_at, updated_at) VALUES(?, ?, ?, ?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET last_known_name=excluded.last_known_name, updated_at=excluded.updated_at")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.setLong(3, now);
                statement.setLong(4, now);
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not update player profile name for " + uuid, exception);
            }
        }, executor);
    }


    public CompletableFuture<RatingRow> getRating(UUID uuid, String ladder) {
        return CompletableFuture.supplyAsync(() -> {
            requireInitialized();
            try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(
                    "SELECT rating,wins,losses,streak,peak_rating FROM player_ratings WHERE uuid=? AND ladder=?")) {
                ps.setString(1, uuid.toString()); ps.setString(2, ladder.toLowerCase(java.util.Locale.ROOT));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? new RatingRow(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5))
                            : new RatingRow(1000, 0, 0, 0, 1000);
                }
            } catch (SQLException e) { throw new IllegalStateException("Could not load rating", e); }
        }, executor);
    }

    public CompletableFuture<Void> recordRankedResult(UUID winner, UUID loser, String ladder) {
        return CompletableFuture.runAsync(() -> {
            requireInitialized();
            String key = ladder.toLowerCase(java.util.Locale.ROOT);
            try (Connection c = openConnection()) {
                c.setAutoCommit(false);
                try {
                    RatingRow w = ratingInTransaction(c, winner, key);
                    RatingRow l = ratingInTransaction(c, loser, key);
                    int nextW = RatingMath.newRating(w.rating(), l.rating(), true, 32, 100);
                    int nextL = RatingMath.newRating(l.rating(), w.rating(), false, 32, 100);
                    upsertRating(c, winner, key, nextW, w.wins()+1, w.losses(), w.streak()+1, Math.max(w.peak(), nextW));
                    upsertRating(c, loser, key, nextL, l.wins(), l.losses()+1, 0, l.peak());
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO match_history(ladder,winner_uuid,loser_uuid,winner_before,loser_before,winner_after,loser_after,played_at) VALUES(?,?,?,?,?,?,?,?)")) {
                        ps.setString(1,key); ps.setString(2,winner.toString()); ps.setString(3,loser.toString());
                        ps.setInt(4,w.rating()); ps.setInt(5,l.rating()); ps.setInt(6,nextW); ps.setInt(7,nextL); ps.setLong(8,System.currentTimeMillis()); ps.executeUpdate();
                    }
                    c.commit();
                } catch (Exception e) { c.rollback(); throw e; }
                finally { c.setAutoCommit(true); }
            } catch (Exception e) { throw new IllegalStateException("Could not persist ranked result", e); }
        }, executor);
    }

    private RatingRow ratingInTransaction(Connection c, UUID uuid, String ladder) throws SQLException {
        try (PreparedStatement ps=c.prepareStatement("SELECT rating,wins,losses,streak,peak_rating FROM player_ratings WHERE uuid=? AND ladder=?")) {
            ps.setString(1,uuid.toString()); ps.setString(2,ladder);
            try (ResultSet rs=ps.executeQuery()) { return rs.next()?new RatingRow(rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getInt(4),rs.getInt(5)):new RatingRow(1000,0,0,0,1000); }
        }
    }
    private void upsertRating(Connection c, UUID uuid, String ladder, int rating, int wins, int losses, int streak, int peak) throws SQLException {
        try (PreparedStatement ps=c.prepareStatement("INSERT INTO player_ratings(uuid,ladder,rating,wins,losses,streak,peak_rating,updated_at) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(uuid,ladder) DO UPDATE SET rating=excluded.rating,wins=excluded.wins,losses=excluded.losses,streak=excluded.streak,peak_rating=excluded.peak_rating,updated_at=excluded.updated_at")) {
            ps.setString(1,uuid.toString()); ps.setString(2,ladder); ps.setInt(3,rating); ps.setInt(4,wins); ps.setInt(5,losses); ps.setInt(6,streak); ps.setInt(7,peak); ps.setLong(8,System.currentTimeMillis()); ps.executeUpdate();
        }
    }
    public record RatingRow(int rating, int wins, int losses, int streak, int peak) { }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
    }

    private void requireInitialized() {
        if (!initialized) throw new IllegalStateException("Database has not been initialized");
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(8, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public File getDatabaseFile() {
        return databaseFile;
    }
}
