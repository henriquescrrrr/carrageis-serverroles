package pt.henrique.serverroles.placeholder;

import pt.henrique.serverroles.manager.PlayerRoleManager;
import pt.henrique.serverroles.model.Role;
import pt.henrique.serverroles.model.RolePlayer;
import pt.henrique.serverroles.util.MessageUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * PlaceholderAPI expansion for ServerRoles.
 * <p>
 * This class is only instantiated when PlaceholderAPI is confirmed present
 * and enabled on the server. The guard check in
 * {@code ServerRoles.hookPlaceholderAPI()} ensures
 * {@link PlaceholderExpansion} is on the classpath before this class is loaded.
 * <p>
 * Supported placeholders (identifier: {@code serverroles}):
 * <ul>
 *     <li>{@code %serverroles_role_id%} — internal role ID</li>
 *     <li>{@code %serverroles_role_name%} — display name</li>
 *     <li>{@code %serverroles_prefix%} — plain text prefix</li>
 *     <li>{@code %serverroles_prefix_formatted%} — prefix with hex color applied (raw MiniMessage)</li>
 *     <li>{@code %serverroles_color%} — hex color code (e.g. {@code #ff5555})</li>
 *     <li>{@code %serverroles_priority%} — role priority</li>
 *     <li>{@code %serverroles_is_op%} — {@code "true"} or {@code "false"}</li>
 *     <li>{@code %serverroles_is_temp%} — {@code "true"} or {@code "false"}</li>
 *     <li>{@code %serverroles_expiry%} — expiry date or {@code "permanent"}</li>
 * </ul>
 */
public class ServerRolesExpansion extends PlaceholderExpansion {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final PlayerRoleManager playerRoleManager;
    private final String pluginVersion;

    public ServerRolesExpansion(PlayerRoleManager playerRoleManager, String pluginVersion) {
        this.playerRoleManager = playerRoleManager;
        this.pluginVersion = pluginVersion;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "serverroles";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ServerRoles";
    }

    @Override
    public @NotNull String getVersion() {
        return pluginVersion;
    }

    /**
     * Keeps the expansion registered across {@code /papi reload}.
     */
    @Override
    public boolean persist() {
        return true;
    }

    /**
     * We do not depend on a specific plugin being enabled — we only need
     * our own {@link PlayerRoleManager} which is always available.
     */
    @Override
    public boolean canRegister() {
        return true;
    }

    /**
     * Resolves a placeholder for the given player.
     * <p>
     * <strong>Important:</strong> this method never returns {@code null}.
     * Returning {@code null} would cause PlaceholderAPI to leave the
     * placeholder text literal (un-replaced) in the output string.
     *
     * @param player the player to resolve for
     * @param params the placeholder parameter (text after {@code serverroles_})
     * @return the resolved value, or an empty string if unknown
     */
    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        Role role = playerRoleManager.getPlayerRole(player.getUniqueId());
        if (role == null) return "";

        RolePlayer rp = playerRoleManager.getPlayer(player.getUniqueId());

        return switch (params.toLowerCase()) {
            case "role_id" -> role.getId();
            case "role_name" -> role.getDisplayName();
            case "prefix" -> role.getPrefix();
            case "prefix_formatted" -> MessageUtil.hexColorText(role.getColor(), role.getPrefix());
            case "color" -> MessageUtil.hexToMiniMessage(role.getColor());  // devolve "<color:#aaaaaa>"
            case "priority" -> String.valueOf(role.getPriority());
            case "is_op" -> String.valueOf(role.isOp());
            case "is_temp" -> String.valueOf(rp != null && rp.isTemporary());
            case "expiry" -> {
                if (rp == null || !rp.isTemporary()) {
                    yield "permanent";
                }
                synchronized (DATE_FORMAT) {
                    yield DATE_FORMAT.format(new Date(rp.getExpiryTimestamp()));
                }
            }
            // Never return null — PAPI leaves the placeholder literal when it gets null.
            default -> "";
        };
    }
}

