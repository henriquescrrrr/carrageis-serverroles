package pt.henrique.serverroles.temp;

import pt.henrique.serverroles.manager.PlayerRoleManager;
import pt.henrique.serverroles.manager.RoleManager;
import pt.henrique.serverroles.model.Role;
import pt.henrique.serverroles.model.RolePlayer;
import pt.henrique.serverroles.permission.PermissionAttachmentManager;
import pt.henrique.serverroles.util.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages temporary role assignments.
 * Runs a periodic check for expired temporary roles and reverts players to the default role.
 */
public class TempRoleManager {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?");
    private static final long CHECK_INTERVAL_TICKS = 5 * 60 * 20L; // 5 minutes in ticks

    private final Plugin plugin;
    private final PlayerRoleManager playerRoleManager;
    private final RoleManager roleManager;
    private final PermissionAttachmentManager permissionManager;
    private final Logger logger;
    private final LangManager langManager;
    private BukkitTask checkTask;

    public TempRoleManager(Plugin plugin, PlayerRoleManager playerRoleManager,
                           RoleManager roleManager, PermissionAttachmentManager permissionManager,
                           Logger logger, LangManager langManager) {
        this.plugin = plugin;
        this.playerRoleManager = playerRoleManager;
        this.roleManager = roleManager;
        this.permissionManager = permissionManager;
        this.logger = logger;
        this.langManager = langManager;
    }

    /**
     * Starts the periodic expiry-check task (every 5 minutes on the main thread).
     */
    public void start() {
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkExpiredRoles,
                CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
        logger.info("Temporary role check task started (every 5 minutes).");
    }

    /**
     * Cancels the periodic expiry-check task.
     */
    public void stop() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
    }

    /**
     * Iterates all online players and expires any whose temporary role has lapsed.
     * Must be called from the main server thread.
     */
    public void checkExpiredRoles() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkAndExpire(player);
        }
    }

    /**
     * Checks whether the given player's temporary role has expired and, if so,
     * reverts them to the default role and notifies them.
     *
     * @param player the online player to check
     * @return {@code true} if the role was expired and reverted
     */
    public boolean checkAndExpire(Player player) {
        if (player == null || !player.isOnline()) return false;

        UUID uuid = player.getUniqueId();
        RolePlayer rp = playerRoleManager.getPlayer(uuid);
        if (rp == null || !rp.isTemporary()) return false;

        if (!rp.isExpired()) return false;

        String oldRoleId = rp.getRoleId();
        Role oldRole = roleManager.getRole(oldRoleId);
        String defaultId = roleManager.getDefaultRoleId();

        // Revert to default (expiry = -1 → permanent).
        playerRoleManager.setPlayerRole(uuid, defaultId, -1L);
        permissionManager.recalculatePermissions(player);

        // Notify the player using the lang system.
        String roleName    = oldRole != null ? oldRole.getDisplayName() : oldRoleId;
        Role   defaultRole = roleManager.getDefaultRole();
        String defaultName = defaultRole != null ? defaultRole.getDisplayName() : defaultId;

        player.sendMessage(langManager.get("role-expired",
                Map.of("role", roleName, "default", defaultName)));

        logger.info("Temporary role expired for " + player.getName() +
                ": " + oldRoleId + " -> " + defaultId);
        return true;
    }

    // -------------------------------------------------------------------------
    // Static duration utilities
    // -------------------------------------------------------------------------

    /**
     * Parses a human-readable duration string into milliseconds.
     * Supported units: {@code d} (days), {@code h} (hours), {@code m} (minutes).
     * Combinations such as {@code "1d12h"} are accepted.
     *
     * @param durationStr the duration string (e.g. {@code "7d"}, {@code "24h"}, {@code "1d12h"})
     * @return the duration in milliseconds, or {@code -1} if the string is invalid
     */
    public static long parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isBlank()) {
            return -1L;
        }

        Matcher matcher = DURATION_PATTERN.matcher(durationStr.toLowerCase());
        if (!matcher.matches()) {
            return -1L;
        }

        String days    = matcher.group(1);
        String hours   = matcher.group(2);
        String minutes = matcher.group(3);

        if (days == null && hours == null && minutes == null) {
            return -1L;
        }

        // Cap the digit count so an absurdly long number can neither throw an
        // uncaught NumberFormatException nor silently overflow the millisecond
        // arithmetic into a bogus (possibly small/negative) expiry. 7 digits of
        // days already exceeds 2700 years.
        if (tooManyDigits(days) || tooManyDigits(hours) || tooManyDigits(minutes)) {
            return -1L;
        }

        try {
            long totalMs = 0;
            if (days    != null) totalMs = Math.addExact(totalMs, Math.multiplyExact(Long.parseLong(days),    24L * 60 * 60 * 1000));
            if (hours   != null) totalMs = Math.addExact(totalMs, Math.multiplyExact(Long.parseLong(hours),        60L * 60 * 1000));
            if (minutes != null) totalMs = Math.addExact(totalMs, Math.multiplyExact(Long.parseLong(minutes),           60L * 1000));
            return totalMs > 0 ? totalMs : -1L;
        } catch (ArithmeticException | NumberFormatException e) {
            return -1L; // overflow or unparseable → treat as invalid duration
        }
    }

    private static boolean tooManyDigits(String value) {
        return value != null && value.length() > 7;
    }

    /**
     * Formats a duration in milliseconds into a short human-readable string
     * (e.g. {@code "1d 12h 30m"}).
     *
     * @param durationMs the duration in milliseconds; {@code ≤ 0} returns {@code "permanent"}
     * @return the formatted duration string
     */
    public static String formatDuration(long durationMs) {
        if (durationMs <= 0) return "permanent";

        long totalMinutes = durationMs / (60 * 1000);
        long days    = totalMinutes / (24 * 60);
        long hours   = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;

        StringBuilder sb = new StringBuilder();
        if (days    > 0) sb.append(days).append("d ");
        if (hours   > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m");
        return sb.toString().trim();
    }

    /**
     * Returns how much time remains until the given expiry timestamp as a
     * human-readable string.
     *
     * @param expiryTimestamp the expiry timestamp in milliseconds
     * @return remaining duration string, {@code "expired"} if past, or
     *         {@code "permanent"} if {@code ≤ 0}
     */
    public static String formatRemaining(long expiryTimestamp) {
        if (expiryTimestamp <= 0) return "permanent";
        long remaining = expiryTimestamp - System.currentTimeMillis();
        if (remaining <= 0) return "expired";
        return formatDuration(remaining);
    }
}
