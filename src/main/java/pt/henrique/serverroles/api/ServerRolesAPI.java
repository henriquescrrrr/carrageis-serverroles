package pt.henrique.serverroles.api;

import pt.henrique.serverroles.model.Role;

import java.util.List;
import java.util.UUID;

/**
 * Public API interface for ServerRoles.
 * Other plugins can use this to interact with the role system.
 * Obtain an instance via {@link ServerRolesProvider#get()}.
 */
public interface ServerRolesAPI {

    /**
     * Gets a role by its ID.
     *
     * @param id the role ID (e.g. "admin")
     * @return the Role, or null if not found
     */
    Role getRoleById(String id);

    /**
     * Gets all registered roles.
     *
     * @return a list of all roles
     */
    List<Role> getAllRoles();

    /**
     * Gets the role assigned to a player.
     *
     * @param uuid the player's UUID
     * @return the player's Role (never null; returns default if unassigned)
     */
    Role getPlayerRole(UUID uuid);

    /**
     * Assigns a permanent role to a player.
     *
     * @param uuid   the player's UUID
     * @param roleId the role ID to assign
     */
    void setPlayerRole(UUID uuid, String roleId);

    /**
     * Assigns a temporary role to a player.
     *
     * @param uuid                the player's UUID
     * @param roleId              the role ID to assign
     * @param expiryTimestampMillis the Unix timestamp (ms) at which the role expires
     */
    void setPlayerRoleTemp(UUID uuid, String roleId, long expiryTimestampMillis);

    /**
     * Checks if a player's role grants a specific permission.
     *
     * @param uuid       the player's UUID
     * @param permission the permission node to check
     * @return true if the permission is granted
     */
    boolean hasPermission(UUID uuid, String permission);

    /**
     * Registers a new role. If a role with the same ID exists, it will be overwritten.
     *
     * @param role the role to register
     */
    void registerRole(Role role);

    /**
     * Checks if a player's current role assignment is temporary.
     *
     * @param uuid the player's UUID
     * @return true if the role is temporary
     */
    boolean isTemporary(UUID uuid);

    /**
     * Gets the expiry timestamp for a player's role assignment.
     *
     * @param uuid the player's UUID
     * @return the expiry timestamp in milliseconds, or -1 if permanent
     */
    long getExpiry(UUID uuid);
}

