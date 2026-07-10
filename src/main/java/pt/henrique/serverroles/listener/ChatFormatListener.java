package pt.henrique.serverroles.listener;

import pt.henrique.serverroles.manager.PlayerRoleManager;
import pt.henrique.serverroles.model.Role;
import pt.henrique.serverroles.util.MessageUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Formats player chat messages with role prefix and color.
 * Uses Paper's AsyncChatEvent for modern chat handling.
 * Format: {prefix} {color}{playername}: {message in white}
 */
public class ChatFormatListener implements Listener {

    private final PlayerRoleManager playerRoleManager;
    private final boolean enabled;

    public ChatFormatListener(PlayerRoleManager playerRoleManager, boolean enabled) {
        this.playerRoleManager = playerRoleManager;
        this.enabled = enabled;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        // AsyncChatEvent fires off the main thread: use the cache-only resolver so
        // we never perform storage I/O (or an implicit create/save) from here.
        // Players are cached on join before they can chat; the default role is a
        // safe fallback for the brief window during /role reload.
        Role role = playerRoleManager.getCachedPlayerRole(player.getUniqueId());
        if (role == null) return;

        String prefix = role.getPrefix();
        String hexColor = role.getColor();
        String playerName = player.getName();

        // Use renderer to set the format
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            // Build prefix and name colored with the role's hex color
            String coloredPrefix = MessageUtil.hexColorText(hexColor, prefix);
            String coloredName = MessageUtil.hexColorText(hexColor, playerName);
            String miniMessageStr = coloredPrefix + " " + coloredName + ": ";
            Component prefixAndName = MessageUtil.parse(miniMessageStr);
            // Color the message white using Adventure API
            Component whiteMessage = message.colorIfAbsent(NamedTextColor.WHITE);
            return prefixAndName.append(whiteMessage);
        });
    }
}

