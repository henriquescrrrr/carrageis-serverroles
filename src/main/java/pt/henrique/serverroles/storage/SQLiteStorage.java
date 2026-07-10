package pt.henrique.serverroles.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import pt.henrique.serverroles.model.Role;
import pt.henrique.serverroles.model.RolePlayer;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite-based storage backend using HikariCP for connection pooling.
 */
public class SQLiteStorage implements StorageManager {

    private final File dataFolder;
    private final Logger logger;
    private HikariDataSource dataSource;

    public SQLiteStorage(File dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    @Override
    public void init() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File dbFile = new File(dataFolder, "serverroles.db");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(1); // SQLite only supports single writer
        config.setPoolName("ServerRoles-SQLite");
        // SQLite-specific: disable auto-commit for connection reuse
        config.setConnectionTestQuery("SELECT 1");

        dataSource = new HikariDataSource(config);

        createTables();
        logger.info("SQLite storage initialized.");
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS roles (
                    id TEXT PRIMARY KEY,
                    displayName TEXT NOT NULL,
                    prefix TEXT NOT NULL DEFAULT '',
                    color TEXT NOT NULL DEFAULT '',
                    priority INTEGER NOT NULL DEFAULT 0,
                    isOp INTEGER NOT NULL DEFAULT 0,
                    inheritFrom TEXT NOT NULL DEFAULT '',
                    permissions TEXT NOT NULL DEFAULT ''
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid TEXT PRIMARY KEY,
                    roleId TEXT NOT NULL DEFAULT 'player',
                    expiryTimestamp INTEGER NOT NULL DEFAULT -1
                )
            """);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create SQLite tables", e);
        }
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public Map<String, Role> loadRoles() {
        Map<String, Role> roles = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM roles ORDER BY priority DESC");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                String displayName = rs.getString("displayName");
                String prefix = rs.getString("prefix");
                String color = rs.getString("color");
                int priority = rs.getInt("priority");
                boolean isOp = rs.getInt("isOp") == 1;
                String inheritFrom = rs.getString("inheritFrom");
                String permsStr = rs.getString("permissions");
                List<String> permissions = permsStr.isEmpty() ? new ArrayList<>() :
                        new ArrayList<>(Arrays.asList(permsStr.split(",")));

                roles.put(id, new Role(id, displayName, prefix, color, priority, isOp, inheritFrom, permissions));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load roles from SQLite", e);
        }
        return roles;
    }

    @Override
    public void saveRole(Role role) {
        if (role == null) return;
        String perms = String.join(",", role.getPermissionsMutable());
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO roles (id, displayName, prefix, color, priority, isOp, inheritFrom, permissions)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
             """)) {
            ps.setString(1, role.getId());
            ps.setString(2, role.getDisplayName());
            ps.setString(3, role.getPrefix());
            ps.setString(4, role.getColor());
            ps.setInt(5, role.getPriority());
            ps.setInt(6, role.isOp() ? 1 : 0);
            ps.setString(7, role.getInheritFrom());
            ps.setString(8, perms);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save role to SQLite: " + role.getId(), e);
        }
    }

    @Override
    public void deleteRole(String roleId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM roles WHERE id = ?")) {
            ps.setString(1, roleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete role from SQLite: " + roleId, e);
        }
    }

    @Override
    public void saveAllRoles(Map<String, Role> roles) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM roles");
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO roles (id, displayName, prefix, color, priority, isOp, inheritFrom, permissions)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
                for (Role role : roles.values()) {
                    ps.setString(1, role.getId());
                    ps.setString(2, role.getDisplayName());
                    ps.setString(3, role.getPrefix());
                    ps.setString(4, role.getColor());
                    ps.setInt(5, role.getPriority());
                    ps.setInt(6, role.isOp() ? 1 : 0);
                    ps.setString(7, role.getInheritFrom());
                    ps.setString(8, String.join(",", role.getPermissionsMutable()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save all roles to SQLite", e);
        }
    }

    @Override
    public Map<UUID, RolePlayer> loadPlayers() {
        Map<UUID, RolePlayer> players = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM players");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String roleId = rs.getString("roleId");
                long expiry = rs.getLong("expiryTimestamp");
                players.put(uuid, new RolePlayer(uuid, roleId, expiry));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load players from SQLite", e);
        }
        return players;
    }

    @Override
    public RolePlayer loadPlayer(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String roleId = rs.getString("roleId");
                    long expiry = rs.getLong("expiryTimestamp");
                    return new RolePlayer(uuid, roleId, expiry);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load player from SQLite: " + uuid, e);
        }
        return null;
    }

    @Override
    public void savePlayer(RolePlayer rolePlayer) {
        if (rolePlayer == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO players (uuid, roleId, expiryTimestamp)
                VALUES (?, ?, ?)
             """)) {
            ps.setString(1, rolePlayer.getUuid().toString());
            ps.setString(2, rolePlayer.getRoleId());
            ps.setLong(3, rolePlayer.getExpiryTimestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save player to SQLite: " + rolePlayer.getUuid(), e);
        }
    }

    @Override
    public void saveAllPlayers(Map<UUID, RolePlayer> players) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM players");
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO players (uuid, roleId, expiryTimestamp) VALUES (?, ?, ?)
            """)) {
                for (RolePlayer rp : players.values()) {
                    ps.setString(1, rp.getUuid().toString());
                    ps.setString(2, rp.getRoleId());
                    ps.setLong(3, rp.getExpiryTimestamp());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save all players to SQLite", e);
        }
    }
}

