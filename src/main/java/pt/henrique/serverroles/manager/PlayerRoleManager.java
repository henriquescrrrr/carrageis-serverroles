package pt.henrique.serverroles.manager;

import pt.henrique.serverroles.event.PlayerRoleChangeEvent;
import pt.henrique.serverroles.model.Role;
import pt.henrique.serverroles.model.RolePlayer;
import pt.henrique.serverroles.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages player-role associations.
 * Handles assigning, removing, and querying player roles.
 * Thread-safe via ConcurrentHashMap.
 */
public class PlayerRoleManager {

    private final StorageManager storage;
    private final RoleManager roleManager;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, RolePlayer> players = new ConcurrentHashMap<>();

    public PlayerRoleManager(StorageManager storage, RoleManager roleManager, Logger logger) {
        this.storage = storage;
        this.roleManager = roleManager;
        this.logger = logger;
    }

    /**
     * Loads all player data from storage.
     */
    public void loadPlayers() {
        players.clear();
        Map<UUID, RolePlayer> loaded = storage.loadPlayers();
        players.putAll(loaded);
        logger.info("Loaded " + players.size() + " player role assignments.");
    }

    /**
     * Gets or creates a RolePlayer for the given UUID.
     *
     * @param uuid the player's UUID
     * @return the RolePlayer (never null)
     */
    public RolePlayer getOrCreatePlayer(UUID uuid) {
        return players.computeIfAbsent(uuid, id -> {
            RolePlayer fromStorage = storage.loadPlayer(id);
            if (fromStorage != null) {
                return fromStorage;
            }
            RolePlayer newPlayer = new RolePlayer(id, roleManager.getDefaultRoleId());
            storage.savePlayer(newPlayer);
            return newPlayer;
        });
    }

    /**
     * Gets a RolePlayer by UUID, or null if not cached.
     *
     * @param uuid the player's UUID
     * @return the RolePlayer, or null
     */
    public RolePlayer getPlayer(UUID uuid) {
        return players.get(uuid);
    }

    /**
     * Returns the Role object for a player.
     *
     * @param uuid the player's UUID
     * @return the player's Role (falls back to default if not found)
     */
    public Role getPlayerRole(UUID uuid) {
        RolePlayer rp = getOrCreatePlayer(uuid);
        Role role = roleManager.getRole(rp.getRoleId());
        if (role == null) {
            role = roleManager.getDefaultRole();
        }
        return role;
    }

    /**
     * Resolves a player's Role using ONLY the in-memory cache — never touches
     * storage and never creates/persists an entry. Falls back to the default
     * role when the player is not cached or their role id is unknown.
     * <p>
     * Use this from asynchronous contexts (e.g. the chat renderer): the
     * {@link #getPlayerRole(UUID)} path can trigger blocking storage I/O and a
     * write via {@link #getOrCreatePlayer(UUID)}, which must not run off the
     * main thread.
     *
     * @param uuid the player's UUID
     * @return the cached Role, or the default role (never the player's, if uncached)
     */
    public Role getCachedPlayerRole(UUID uuid) {
        RolePlayer rp = players.get(uuid);
        Role role = rp != null ? roleManager.getRole(rp.getRoleId()) : null;
        return role != null ? role : roleManager.getDefaultRole();
    }

    /**
     * Sets a player's role permanently. Fires PlayerRoleChangeEvent.
     *
     * @param uuid   the player's UUID
     * @param roleId the new role ID
     * @return true if the role was set (event not cancelled), false otherwise
     */
    public boolean setPlayerRole(UUID uuid, String roleId) {
        return setPlayerRole(uuid, roleId, -1L);
    }

    /**
     * Sets a player's role with an optional expiry. Fires PlayerRoleChangeEvent.
     *
     * @param uuid            the player's UUID
     * @param roleId          the new role ID
     * @param expiryTimestamp  the expiry timestamp in ms, or -1 for permanent
     * @return true if the role was set (event not cancelled), false otherwise
     */
    public boolean setPlayerRole(UUID uuid, String roleId, long expiryTimestamp) {
        Role newRole = roleManager.getRole(roleId);
        if (newRole == null) {
            return false;
        }

        RolePlayer rp = getOrCreatePlayer(uuid);
        Role oldRole = roleManager.getRole(rp.getRoleId());
        boolean temporary = expiryTimestamp > 0;

        // Fire event on main thread
        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            PlayerRoleChangeEvent event = new PlayerRoleChangeEvent(
                    onlinePlayer, oldRole, newRole, temporary, expiryTimestamp);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return false;
            }
        }

        rp.setRoleId(roleId);
        rp.setExpiryTimestamp(expiryTimestamp);
        storage.savePlayer(rp);

        logger.info("Set role of " + uuid + " to " + roleId +
                (temporary ? " (expires: " + expiryTimestamp + ")" : " (permanent)"));
        return true;
    }

    /**
     * Resets a player to the default role.
     *
     * @param uuid the player's UUID
     * @return true if reset was successful
     */
    public boolean resetPlayerRole(UUID uuid) {
        return setPlayerRole(uuid, roleManager.getDefaultRoleId(), -1L);
    }

    /**
     * Checks if a player's role assignment is temporary.
     *
     * @param uuid the player's UUID
     * @return true if temporary
     */
    public boolean isTemporary(UUID uuid) {
        RolePlayer rp = players.get(uuid);
        return rp != null && rp.isTemporary();
    }

    /**
     * Gets the expiry timestamp for a player's role.
     *
     * @param uuid the player's UUID
     * @return the expiry timestamp, or -1 if permanent or not found
     */
    public long getExpiry(UUID uuid) {
        RolePlayer rp = players.get(uuid);
        return rp != null ? rp.getExpiryTimestamp() : -1L;
    }

    /**
     * Saves a specific player's data.
     *
     * @param uuid the player's UUID
     */
    public void savePlayer(UUID uuid) {
        RolePlayer rp = players.get(uuid);
        if (rp != null) {
            storage.savePlayer(rp);
        }
    }

    /**
     * Saves all player data.
     */
    public void saveAllPlayers() {
        storage.saveAllPlayers(new HashMap<>(players));
    }

    /**
     * Removes a player from the cache (e.g. on quit).
     * Data is already persisted; this just cleans up memory.
     *
     * @param uuid the player's UUID
     */
    public void unloadPlayer(UUID uuid) {
        // We keep data in cache for potential lookups; only clear on reload
        // But save first
        savePlayer(uuid);
    }

    /**
     * Returns all cached player data.
     *
     * @return unmodifiable map of all players
     */
    public Map<UUID, RolePlayer> getAllPlayers() {
        return Collections.unmodifiableMap(players);
    }

    /**
     * Checks if a player (resolved through their role) has a specific permission.
     *
     * @param uuid       the player's UUID
     * @param permission the permission node
     * @return true if the player's role grants this permission
     */
    public boolean hasPermission(UUID uuid, String permission) {
        Role role = getPlayerRole(uuid);
        Map<String, Boolean> perms = roleManager.resolvePermissions(role);
        Boolean value = perms.get(permission);
        return value != null && value;
    }
}

