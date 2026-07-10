package pt.henrique.serverroles.permission;

import pt.henrique.serverroles.manager.PlayerRoleManager;
import pt.henrique.serverroles.manager.RoleManager;
import pt.henrique.serverroles.model.Role;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages Bukkit PermissionAttachments for online players.
 * Handles applying and removing permissions based on roles,
 * including operator status management.
 */
public class PermissionAttachmentManager {

    private final Plugin plugin;
    private final RoleManager roleManager;
    private final PlayerRoleManager playerRoleManager;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    public PermissionAttachmentManager(Plugin plugin, RoleManager roleManager,
                                       PlayerRoleManager playerRoleManager, Logger logger) {
        this.plugin = plugin;
        this.roleManager = roleManager;
        this.playerRoleManager = playerRoleManager;
        this.logger = logger;
    }

    /**
     * Applies all permissions from the player's role (including inherited) via PermissionAttachment.
     * Also handles op status.
     *
     * @param player the online player
     */
    public void applyPermissions(Player player) {
        if (player == null || !player.isOnline()) return;

        // Remove existing attachment first
        removePermissions(player);

        Role role = playerRoleManager.getPlayerRole(player.getUniqueId());
        if (role == null) {
            role = roleManager.getDefaultRole();
        }

        // Create new attachment
        PermissionAttachment attachment = player.addAttachment(plugin);

        // Resolve all permissions including inheritance
        Map<String, Boolean> permissions = roleManager.resolvePermissions(role);
        for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
            attachment.setPermission(entry.getKey(), entry.getValue());
        }

        attachments.put(player.getUniqueId(), attachment);

        // Handle op status
        player.setOp(role.isOp());

        // Force permission recalculation
        player.recalculatePermissions();

        logger.fine("Applied " + permissions.size() + " permissions to " + player.getName() +
                " (role: " + role.getId() + ", op: " + role.isOp() + ")");
    }

    /**
     * Removes the PermissionAttachment from a player and reverts op status.
     *
     * @param player the player to clean up
     */
    public void removePermissions(Player player) {
        if (player == null) return;

        PermissionAttachment attachment = attachments.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (IllegalArgumentException e) {
                // Attachment already removed (player quit, etc.)
            }
        }

        // Revert op status
        player.setOp(false);
    }

    /**
     * Recalculates and re-applies permissions for a player.
     * Call this after a role change.
     *
     * @param player the player to recalculate
     */
    public void recalculatePermissions(Player player) {
        applyPermissions(player);
    }

    /**
     * Recalculates permissions for all online players.
     * Used during reload.
     */
    public void recalculateAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            applyPermissions(player);
        }
    }

    /**
     * Cleans up all attachments. Called on plugin disable.
     */
    public void cleanup() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            removePermissions(player);
        }
        attachments.clear();
    }

    /**
     * Checks if a player currently has an attachment managed by this system.
     *
     * @param uuid the player's UUID
     * @return true if managed
     */
    public boolean hasAttachment(UUID uuid) {
        return attachments.containsKey(uuid);
    }
}

