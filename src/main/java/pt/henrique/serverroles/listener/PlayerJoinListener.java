package pt.henrique.serverroles.listener;

import pt.henrique.serverroles.manager.PlayerRoleManager;
import pt.henrique.serverroles.permission.PermissionAttachmentManager;
import pt.henrique.serverroles.temp.TempRoleManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.logging.Logger;

/**
 * Handles player join events.
 * Loads player role data, checks for expired temporary roles,
 * and applies permissions.
 */
public class PlayerJoinListener implements Listener {

    private final PlayerRoleManager playerRoleManager;
    private final PermissionAttachmentManager permissionManager;
    private final TempRoleManager tempRoleManager;
    private final Logger logger;

    public PlayerJoinListener(PlayerRoleManager playerRoleManager,
                              PermissionAttachmentManager permissionManager,
                              TempRoleManager tempRoleManager,
                              Logger logger) {
        this.playerRoleManager = playerRoleManager;
        this.permissionManager = permissionManager;
        this.tempRoleManager = tempRoleManager;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load/create player role data
        playerRoleManager.getOrCreatePlayer(player.getUniqueId());

        // Check for expired temporary roles
        tempRoleManager.checkAndExpire(player);

        // Apply permissions
        permissionManager.applyPermissions(player);

        logger.fine("Applied role permissions for " + player.getName());
    }
}

