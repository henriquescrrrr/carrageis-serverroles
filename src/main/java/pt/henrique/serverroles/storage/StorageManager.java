package pt.henrique.serverroles.storage;

import pt.henrique.serverroles.model.Role;
import pt.henrique.serverroles.model.RolePlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interface for all storage backends (YAML, SQLite, MySQL).
 * Implementations must be safe to call from the main thread for reads,
 * but heavy writes may be deferred or batched as needed.
 */
public interface StorageManager {

    /**
     * Initializes the storage backend (create files, tables, connections).
     */
    void init();

    /**
     * Shuts down the storage backend (close connections, flush data).
     */
    void shutdown();

    /**
     * Loads all roles from storage.
     *
     * @return a map of role ID to Role
     */
    Map<String, Role> loadRoles();

    /**
     * Saves a single role to storage.
     *
     * @param role the role to save
     */
    void saveRole(Role role);

    /**
     * Deletes a role from storage.
     *
     * @param roleId the ID of the role to delete
     */
    void deleteRole(String roleId);

    /**
     * Saves all roles to storage (bulk operation).
     *
     * @param roles the map of all roles to save
     */
    void saveAllRoles(Map<String, Role> roles);

    /**
     * Loads all player-role associations from storage.
     *
     * @return a map of player UUID to RolePlayer
     */
    Map<UUID, RolePlayer> loadPlayers();

    /**
     * Loads a single player's role data.
     *
     * @param uuid the player's UUID
     * @return the RolePlayer, or null if not found
     */
    RolePlayer loadPlayer(UUID uuid);

    /**
     * Saves a single player's role data.
     *
     * @param rolePlayer the player-role association to save
     */
    void savePlayer(RolePlayer rolePlayer);

    /**
     * Saves all player data (bulk operation).
     *
     * @param players the map of all player data
     */
    void saveAllPlayers(Map<UUID, RolePlayer> players);
}

