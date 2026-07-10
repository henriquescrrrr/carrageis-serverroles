package pt.henrique.serverroles.storage;

import pt.henrique.serverroles.model.Role;
import pt.henrique.serverroles.model.RolePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * YAML-based storage backend.
 * Stores roles in roles.yml and players in players.yml within the plugin data folder.
 */
public class YamlStorage implements StorageManager {

    private final File dataFolder;
    private final Logger logger;
    private File rolesFile;
    private File playersFile;

    public YamlStorage(File dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    @Override
    public void init() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        rolesFile = new File(dataFolder, "roles.yml");
        playersFile = new File(dataFolder, "players.yml");

        // roles.yml is created by the plugin via saveResource; players.yml we create if missing
        if (!playersFile.exists()) {
            try {
                playersFile.createNewFile();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to create players.yml", e);
            }
        }
        logger.info("YAML storage initialized.");
    }

    @Override
    public void shutdown() {
        // nothing to close for YAML
    }

    @Override
    public Map<String, Role> loadRoles() {
        Map<String, Role> roles = new LinkedHashMap<>();
        if (!rolesFile.exists()) {
            return roles;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(rolesFile);
        ConfigurationSection rolesSection = config.getConfigurationSection("roles");
        if (rolesSection == null) {
            return roles;
        }

        for (String id : rolesSection.getKeys(false)) {
            ConfigurationSection rs = rolesSection.getConfigurationSection(id);
            if (rs == null) continue;

            String displayName = rs.getString("displayName", id);
            String prefix = rs.getString("prefix", "");
            String color = rs.getString("color", "");
            int priority = rs.getInt("priority", 0);
            boolean isOp = rs.getBoolean("isOp", false);
            String inheritFrom = rs.getString("inheritFrom", "");
            List<String> permissions = rs.getStringList("permissions");

            roles.put(id, new Role(id, displayName, prefix, color, priority, isOp, inheritFrom, permissions));
        }

        return roles;
    }

    @Override
    public void saveRole(Role role) {
        if (role == null) return;
        Map<String, Role> all = loadRoles();
        all.put(role.getId(), role);
        saveAllRoles(all);
    }

    @Override
    public void deleteRole(String roleId) {
        Map<String, Role> all = loadRoles();
        all.remove(roleId);
        saveAllRoles(all);
    }

    @Override
    public void saveAllRoles(Map<String, Role> roles) {
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, Role> entry : roles.entrySet()) {
            Role r = entry.getValue();
            String path = "roles." + r.getId();
            config.set(path + ".displayName", r.getDisplayName());
            config.set(path + ".prefix", r.getPrefix());
            config.set(path + ".color", r.getColor());
            config.set(path + ".priority", r.getPriority());
            config.set(path + ".isOp", r.isOp());
            config.set(path + ".inheritFrom", r.getInheritFrom());
            config.set(path + ".permissions", r.getPermissionsMutable());
        }

        try {
            config.save(rolesFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save roles.yml", e);
        }
    }

    @Override
    public Map<UUID, RolePlayer> loadPlayers() {
        Map<UUID, RolePlayer> players = new HashMap<>();
        if (!playersFile.exists()) {
            return players;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(playersFile);
        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection == null) {
            return players;
        }

        for (String uuidStr : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ConfigurationSection ps = playersSection.getConfigurationSection(uuidStr);
                if (ps == null) continue;

                String roleId = ps.getString("role", "player");
                long expiry = ps.getLong("expiry", -1L);
                players.put(uuid, new RolePlayer(uuid, roleId, expiry));
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid UUID in players.yml: " + uuidStr);
            }
        }

        return players;
    }

    @Override
    public RolePlayer loadPlayer(UUID uuid) {
        Map<UUID, RolePlayer> all = loadPlayers();
        return all.get(uuid);
    }

    @Override
    public void savePlayer(RolePlayer rolePlayer) {
        if (rolePlayer == null) return;
        YamlConfiguration config;
        if (playersFile.exists()) {
            config = YamlConfiguration.loadConfiguration(playersFile);
        } else {
            config = new YamlConfiguration();
        }

        String path = "players." + rolePlayer.getUuid().toString();
        config.set(path + ".role", rolePlayer.getRoleId());
        config.set(path + ".expiry", rolePlayer.getExpiryTimestamp());

        try {
            config.save(playersFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save players.yml", e);
        }
    }

    @Override
    public void saveAllPlayers(Map<UUID, RolePlayer> players) {
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, RolePlayer> entry : players.entrySet()) {
            RolePlayer rp = entry.getValue();
            String path = "players." + rp.getUuid().toString();
            config.set(path + ".role", rp.getRoleId());
            config.set(path + ".expiry", rp.getExpiryTimestamp());
        }

        try {
            config.save(playersFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save players.yml", e);
        }
    }
}

