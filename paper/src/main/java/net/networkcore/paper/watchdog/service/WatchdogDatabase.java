package net.networkcore.paper.watchdog.service;

import net.networkcore.paper.NetworkCorePaperPlugin;
import net.networkcore.paper.watchdog.model.WatchdogCategory;
import net.networkcore.paper.watchdog.model.WatchdogIncident;
import net.networkcore.paper.watchdog.model.WatchdogProfile;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class WatchdogDatabase implements AutoCloseable {

    private final NetworkCorePaperPlugin plugin;
    private Connection connection;

    public WatchdogDatabase(NetworkCorePaperPlugin plugin) {
        this.plugin = plugin;
    }

    public void open() {
        try {
            Class.forName("org.sqlite.JDBC");
            File directory = new File(plugin.getDataFolder(), "watchdog");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException("Could not create Watchdog data directory.");
            }
            File databaseFile = new File(directory, "watchdog.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("PRAGMA foreign_keys=ON");
            }
            createSchema();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to open Watchdog database.", exception);
        }
    }

    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS watchdog_profiles (
                        uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        movement_vl REAL NOT NULL DEFAULT 0,
                        combat_vl REAL NOT NULL DEFAULT 0,
                        inventory_vl REAL NOT NULL DEFAULT 0,
                        economy_vl REAL NOT NULL DEFAULT 0,
                        exploit_vl REAL NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS watchdog_incidents (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        check_name TEXT NOT NULL,
                        points REAL NOT NULL,
                        details TEXT,
                        server_id TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_watchdog_incidents_uuid_created
                    ON watchdog_incidents(uuid, created_at DESC)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_watchdog_profiles_name
                    ON watchdog_profiles(name COLLATE NOCASE)
                    """);
        }
    }

    public WatchdogProfile loadProfile(UUID playerId, String playerName) {
        String sql = """
                SELECT name, movement_vl, combat_vl, inventory_vl, economy_vl, exploit_vl
                FROM watchdog_profiles
                WHERE uuid = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                WatchdogProfile profile = new WatchdogProfile(playerId, playerName);
                if (!result.next()) {
                    return profile;
                }
                String storedName = result.getString("name");
                if (storedName != null && !storedName.isBlank()) {
                    profile.playerName(storedName);
                }
                profile.setVl(WatchdogCategory.MOVEMENT, result.getDouble("movement_vl"));
                profile.setVl(WatchdogCategory.COMBAT, result.getDouble("combat_vl"));
                profile.setVl(WatchdogCategory.INVENTORY, result.getDouble("inventory_vl"));
                profile.setVl(WatchdogCategory.ECONOMY, result.getDouble("economy_vl"));
                profile.setVl(WatchdogCategory.EXPLOIT, result.getDouble("exploit_vl"));
                return profile;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load Watchdog profile.", exception);
        }
    }

    public void saveProfile(WatchdogProfile profile) {
        String sql = """
                INSERT INTO watchdog_profiles (
                    uuid, name, movement_vl, combat_vl, inventory_vl, economy_vl, exploit_vl, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    name = excluded.name,
                    movement_vl = excluded.movement_vl,
                    combat_vl = excluded.combat_vl,
                    inventory_vl = excluded.inventory_vl,
                    economy_vl = excluded.economy_vl,
                    exploit_vl = excluded.exploit_vl,
                    updated_at = excluded.updated_at
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.playerId().toString());
            statement.setString(2, profile.playerName());
            statement.setDouble(3, profile.vl(WatchdogCategory.MOVEMENT));
            statement.setDouble(4, profile.vl(WatchdogCategory.COMBAT));
            statement.setDouble(5, profile.vl(WatchdogCategory.INVENTORY));
            statement.setDouble(6, profile.vl(WatchdogCategory.ECONOMY));
            statement.setDouble(7, profile.vl(WatchdogCategory.EXPLOIT));
            statement.setLong(8, Instant.now().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save Watchdog profile.", exception);
        }
    }

    public WatchdogIncident insertIncident(UUID playerId, String playerName, WatchdogCategory category, String checkName, double points, String details, String serverId) {
        String sql = """
                INSERT INTO watchdog_incidents (uuid, name, category, check_name, points, details, server_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        Instant createdAt = Instant.now();
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, playerName);
            statement.setString(3, category.name());
            statement.setString(4, checkName);
            statement.setDouble(5, points);
            statement.setString(6, details);
            statement.setString(7, serverId);
            statement.setLong(8, createdAt.toEpochMilli());
            statement.executeUpdate();
            long id = 0L;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    id = keys.getLong(1);
                }
            }
            return new WatchdogIncident(id, playerId, playerName, category, checkName, points, details, serverId, createdAt);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save Watchdog incident.", exception);
        }
    }

    public List<WatchdogIncident> recentIncidents(UUID playerId, int limit) {
        String sql = """
                SELECT id, name, category, check_name, points, details, server_id, created_at
                FROM watchdog_incidents
                WHERE uuid = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
        List<WatchdogIncident> incidents = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, Math.max(1, Math.min(limit, 100)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    incidents.add(new WatchdogIncident(
                            result.getLong("id"),
                            playerId,
                            result.getString("name"),
                            WatchdogCategory.valueOf(result.getString("category")),
                            result.getString("check_name"),
                            result.getDouble("points"),
                            result.getString("details"),
                            result.getString("server_id"),
                            Instant.ofEpochMilli(result.getLong("created_at"))
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read Watchdog incidents.", exception);
        }
        return incidents;
    }

    public Optional<UUID> findUuidByName(String playerName) {
        String profileSql = "SELECT uuid FROM watchdog_profiles WHERE name = ? COLLATE NOCASE LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(profileSql)) {
            statement.setString(1, playerName);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(UUID.fromString(result.getString("uuid")));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to resolve Watchdog player.", exception);
        }
        String incidentSql = "SELECT uuid FROM watchdog_incidents WHERE name = ? COLLATE NOCASE ORDER BY created_at DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(incidentSql)) {
            statement.setString(1, playerName);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(UUID.fromString(result.getString("uuid")));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to resolve Watchdog player.", exception);
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().warning("[Watchdog] Failed to close SQLite database: " + exception.getMessage());
        }
        connection = null;
    }
}
