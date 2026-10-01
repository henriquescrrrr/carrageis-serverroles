package pt.henrique.serverroles.permission;

import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import pt.henrique.serverroles.manager.RoleManager;
import pt.henrique.serverroles.model.Role;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Reports roles whose resolved permissions reach world-mutating vanilla nodes.
 *
 * <p>Reads only. It never edits a role, a permission or an attachment: changing an
 * existing grant would break live setups silently, which is worse than the grant.
 *
 * <h2>What it looks for</h2>
 * <ol>
 *   <li><b>Op.</b> {@code isOp: true} calls {@code Player#setOp(true)}, which adds the
 *       account to the vanilla op list at the server's {@code op-permission-level}.
 *       Two consequences: every Bukkit node the player does not explicitly hold falls
 *       back to its registered default, and that default is {@code OP} for
 *       {@code minecraft.command.gamerule}, {@code minecraft.command.gamemode} and
 *       {@code minecraft.nbt.copy}; and the 26.x game-rule and game-mode packets accept
 *       the vanilla permission level <em>or</em> the Bukkit node, so negating the Bukkit
 *       node does not stop an op from using either packet.</li>
 *   <li><b>Broad parents.</b> Bukkit expands a granted permission into its registered
 *       children. {@code minecraft} carries {@code minecraft.command} and
 *       {@code minecraft.nbt.copy}; {@code minecraft.command} carries every vanilla
 *       command node. Granting either hands over the sensitive nodes transitively.</li>
 *   <li><b>Direct grants</b> of a sensitive node.</li>
 *   <li><b>Wildcard-shaped nodes</b> ({@code *}, {@code something.*}). ServerRoles applies
 *       nodes verbatim through {@code PermissionAttachment}, and Bukkit matches exact
 *       names, expanding only registered children — it does not interpret {@code *}. Such
 *       a node therefore grants nothing on its own, which is worth saying out loud
 *       because it usually means the operator expected a wildcard and does not have one.</li>
 * </ol>
 *
 * <h2>Timing</h2>
 * <p>CraftBukkit registers the {@code minecraft.*} permission tree at {@code POSTWORLD},
 * after plugin {@code onEnable} has run. Calling this during enable would find no
 * registered children and miss every broad-parent case, so the caller must defer it by
 * at least one tick.
 */
public class PermissionAudit {

    private final RoleManager roleManager;
    private final Logger logger;
    private final List<String> sensitiveNodes;

    public PermissionAudit(RoleManager roleManager, Logger logger, List<String> sensitiveNodes) {
        this.roleManager = roleManager;
        this.logger = logger;
        this.sensitiveNodes = sensitiveNodes.stream()
                .map(node -> node.toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * Runs the audit over every loaded role.
     *
     * @return one line per finding, empty when nothing was found
     */
    public List<String> run() {
        List<String> findings = new ArrayList<>();

        for (Role role : roleManager.getAllRolesSorted()) {
            Map<String, Boolean> resolved = roleManager.resolvePermissions(role);

            // Nodes the role negates explicitly. A negation set on the attachment wins
            // over any child expansion and over the op default for Bukkit checks.
            Set<String> negated = new HashSet<>();
            for (Map.Entry<String, Boolean> entry : resolved.entrySet()) {
                if (!entry.getValue()) negated.add(entry.getKey().toLowerCase(Locale.ROOT));
            }

            if (role.isOp()) {
                List<String> open = sensitiveNodes.stream().filter(n -> !negated.contains(n)).toList();
                findings.add("role '" + role.getId() + "' sets isOp: true"
                        + (open.isEmpty()
                        ? " — the sensitive nodes are negated for Bukkit checks, but op still passes"
                        + " the vanilla permission-level branch of the game-rule and game-mode packets"
                        : " — op grants " + String.join(", ", open)
                        + " by default, and op passes the vanilla permission-level branch of the"
                        + " game-rule and game-mode packets even when those nodes are negated"));
            }

            for (Map.Entry<String, Boolean> entry : resolved.entrySet()) {
                if (!entry.getValue()) continue;
                String node = entry.getKey().toLowerCase(Locale.ROOT);

                if (isWildcardShaped(node)) {
                    findings.add("role '" + role.getId() + "' grants '" + node
                            + "' — ServerRoles applies nodes verbatim and Bukkit does not"
                            + " interpret '*', so this grants nothing unless a plugin registers"
                            + " a permission with that exact name");
                    continue;
                }

                if (sensitiveNodes.contains(node)) {
                    findings.add("role '" + role.getId() + "' grants " + node + " directly");
                    continue;
                }

                List<String> reached = expand(node).stream()
                        .filter(sensitiveNodes::contains)
                        .filter(n -> !negated.contains(n))
                        .toList();
                if (!reached.isEmpty()) {
                    findings.add("role '" + role.getId() + "' grants '" + node
                            + "', which expands to " + String.join(", ", reached));
                }
            }
        }

        return findings;
    }

    /**
     * Writes the audit result to the plugin logger.
     *
     * @return the findings, so a caller can reuse them
     */
    public List<String> logFindings() {
        List<String> findings = run();
        if (findings.isEmpty()) {
            logger.info("Permission audit: no role reaches "
                    + String.join(", ", sensitiveNodes) + ".");
            return findings;
        }

        logger.warning("Permission audit: " + findings.size()
                + " role grant(s) reach world-mutating vanilla permissions.");
        for (String finding : findings) {
            logger.warning("  " + finding);
        }
        logger.warning("  A client can set a game rule or its own game mode by packet on these"
                + " nodes, with no command to intercept. To keep a broad grant without them, add"
                + " the negated node after it on the role, or on a child role:"
                + " -minecraft.command.gamerule, -minecraft.command.gamemode, -minecraft.nbt.copy."
                + " Negation does not restrain an op.");
        logger.warning("  Nothing was changed. Edit roles.yml or use /role addperm to act on this.");
        return findings;
    }

    // ─── Node expansion ───────────────────────────────────────────────────────

    /**
     * Returns every permission a single granted node resolves to, mirroring
     * {@code PermissibleBase.calculateChildPermissions}: a child's effective value is
     * its declared value XOR the inherited inversion, and recursion inverts again.
     */
    private Set<String> expand(String node) {
        Set<String> granted = new LinkedHashSet<>();
        granted.add(node);
        Permission permission = Bukkit.getPluginManager().getPermission(node);
        if (permission != null) {
            collect(permission.getChildren(), false, granted, new HashSet<>());
        }
        return granted;
    }

    private void collect(Map<String, Boolean> children, boolean invert,
                         Set<String> granted, Set<String> seen) {
        for (Map.Entry<String, Boolean> entry : children.entrySet()) {
            String name = entry.getKey().toLowerCase(Locale.ROOT);
            boolean value = entry.getValue() ^ invert;
            if (!seen.add(name + "=" + value)) continue;

            if (value) {
                granted.add(name);
            } else {
                granted.remove(name);
            }

            Permission child = Bukkit.getPluginManager().getPermission(name);
            if (child != null) {
                collect(child.getChildren(), !value, granted, seen);
            }
        }
    }

    private static boolean isWildcardShaped(String node) {
        return node.equals("*") || node.endsWith(".*");
    }
}
