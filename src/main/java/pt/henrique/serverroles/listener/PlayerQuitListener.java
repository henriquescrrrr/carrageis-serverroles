package pt.henrique.serverroles.listener;

import pt.henrique.serverroles.manager.PlayerRoleManager;
import pt.henrique.serverroles.permission.PermissionAttachmentManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Logger;

/**
 * Handles player quit events.
 * Saves player data, removes permission attachments, and reverts op status.
 */
public class PlayerQuitListener implements Listener {

    private final PlayerRoleManager playerRoleManager;
    private final PermissionAttachmentManager permissionManager;
    private final Logger logger;

    public PlayerQuitListener(PlayerRoleManager playerRoleManager,
                              PermissionAttachmentManager permissionManager,
                              Logger logger) {
        this.playerRoleManager = playerRoleManager;
        this.permissionManager = permissionManager;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Remove permissions and revert op
        permissionManager.removePermissions(player);

        // Save player data
        playerRoleManager.unloadPlayer(player.getUniqueId());

        logger.fine("Cleaned up role data for " + player.getName());
    }
}

