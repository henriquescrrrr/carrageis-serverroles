package pt.henrique.serverroles.model;

import java.util.UUID;

/**
 * Represents the association between a player and their assigned role.
 * Tracks the player's UUID, their current role ID, and an optional expiry timestamp
 * for temporary roles.
 */
public class RolePlayer {

    private final UUID uuid;
    private String roleId;
    private long expiryTimestamp; // -1 = permanent

    /**
     * Constructs a new RolePlayer.
     *
     * @param uuid            the player's unique identifier
     * @param roleId          the ID of the assigned role
     * @param expiryTimestamp  the expiry timestamp in milliseconds, or -1 for permanent
     */
    public RolePlayer(UUID uuid, String roleId, long expiryTimestamp) {
        this.uuid = uuid;
        this.roleId = roleId != null ? roleId : "player";
        this.expiryTimestamp = expiryTimestamp;
    }

    /**
     * Constructs a permanent RolePlayer.
     *
     * @param uuid   the player's unique identifier
     * @param roleId the ID of the assigned role
     */
    public RolePlayer(UUID uuid, String roleId) {
        this(uuid, roleId, -1L);
    }

    /** @return the player's UUID */
    public UUID getUuid() {
        return uuid;
    }

    /** @return the ID of the player's current role */
    public String getRoleId() {
        return roleId;
    }

    /** @param roleId the new role ID */
    public void setRoleId(String roleId) {
        this.roleId = roleId != null ? roleId : "player";
    }

    /** @return the expiry timestamp in milliseconds, or -1 if permanent */
    public long getExpiryTimestamp() {
        return expiryTimestamp;
    }

    /** @param expiryTimestamp the new expiry timestamp, or -1 for permanent */
    public void setExpiryTimestamp(long expiryTimestamp) {
        this.expiryTimestamp = expiryTimestamp;
    }

    /** @return true if this role assignment is temporary (has a finite expiry) */
    public boolean isTemporary() {
        return expiryTimestamp > 0;
    }

    /** @return true if this temporary role has expired */
    public boolean isExpired() {
        return isTemporary() && System.currentTimeMillis() >= expiryTimestamp;
    }

    @Override
    public String toString() {
        return "RolePlayer{uuid=" + uuid + ", roleId='" + roleId + "', expiry=" + expiryTimestamp + "}";
    }
}

