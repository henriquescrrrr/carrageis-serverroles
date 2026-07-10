package pt.henrique.serverroles.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import pt.henrique.serverroles.model.Role;
import pt.henrique.serverroles.model.RolePlayer;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MySQL-based storage backend using HikariCP for connection pooling.
 */
public class MySQLStorage implements StorageManager {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final Logger logger;
    private HikariDataSource dataSource;

    public MySQLStorage(String host, int port, String database, String username, String password, Logger logger) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.logger = logger;
    }

    @Override
    public void init() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setPoolName("ServerRoles-MySQL");
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
        createTables();
        logger.info("MySQL storage initialized.");
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sr_roles (
                    id VARCHAR(64) PRIMARY KEY,
                    displayName VARCHAR(128) NOT NULL,
                    prefix VARCHAR(256) NOT NULL DEFAULT '',
                    color VARCHAR(128) NOT NULL DEFAULT '',
                    priority INT NOT NULL DEFAULT 0,
                    isOp TINYINT(1) NOT NULL DEFAULT 0,
                    inheritFrom VARCHAR(64) NOT NULL DEFAULT '',
                    permissions TEXT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sr_players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    roleId VARCHAR(64) NOT NULL DEFAULT 'player',
                    expiryTimestamp BIGINT NOT NULL DEFAULT -1
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create MySQL tables", e);
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
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sr_roles ORDER BY priority DESC");
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
                List<String> permissions = (permsStr == null || permsStr.isEmpty()) ? new ArrayList<>() :
                        new ArrayList<>(Arrays.asList(permsStr.split(",")));

                roles.put(id, new Role(id, displayName, prefix, color, priority, isOp, inheritFrom, permissions));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load roles from MySQL", e);
        }
        return roles;
    }

    @Override
    public void saveRole(Role role) {
        if (role == null) return;
        String perms = String.join(",", role.getPermissionsMutable());
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sr_roles (id, displayName, prefix, color, priority, isOp, inheritFrom, permissions)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    displayName = VALUES(displayName),
                    prefix = VALUES(prefix),
                    color = VALUES(color),
                    priority = VALUES(priority),
                    isOp = VALUES(isOp),
                    inheritFrom = VALUES(inheritFrom),
                    permissions = VALUES(permissions)
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
            logger.log(Level.SEVERE, "Failed to save role to MySQL: " + role.getId(), e);
        }
    }

    @Override
    public void deleteRole(String roleId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM sr_roles WHERE id = ?")) {
            ps.setString(1, roleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete role from MySQL: " + roleId, e);
        }
    }

    @Override
    public void saveAllRoles(Map<String, Role> roles) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM sr_roles");
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sr_roles (id, displayName, prefix, color, priority, isOp, inheritFrom, permissions)
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
            logger.log(Level.SEVERE, "Failed to save all roles to MySQL", e);
        }
    }

    @Override
    public Map<UUID, RolePlayer> loadPlayers() {
        Map<UUID, RolePlayer> players = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sr_players");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String roleId = rs.getString("roleId");
                long expiry = rs.getLong("expiryTimestamp");
                players.put(uuid, new RolePlayer(uuid, roleId, expiry));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load players from MySQL", e);
        }
        return players;
    }

    @Override
    public RolePlayer loadPlayer(UUID uuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sr_players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String roleId = rs.getString("roleId");
                    long expiry = rs.getLong("expiryTimestamp");
                    return new RolePlayer(uuid, roleId, expiry);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load player from MySQL: " + uuid, e);
        }
        return null;
    }

    @Override
    public void savePlayer(RolePlayer rolePlayer) {
        if (rolePlayer == null) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sr_players (uuid, roleId, expiryTimestamp)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    roleId = VALUES(roleId),
                    expiryTimestamp = VALUES(expiryTimestamp)
             """)) {
            ps.setString(1, rolePlayer.getUuid().toString());
            ps.setString(2, rolePlayer.getRoleId());
            ps.setLong(3, rolePlayer.getExpiryTimestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save player to MySQL: " + rolePlayer.getUuid(), e);
        }
    }

    @Override
    public void saveAllPlayers(Map<UUID, RolePlayer> players) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM sr_players");
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sr_players (uuid, roleId, expiryTimestamp) VALUES (?, ?, ?)
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
            logger.log(Level.SEVERE, "Failed to save all players to MySQL", e);
        }
    }
}

