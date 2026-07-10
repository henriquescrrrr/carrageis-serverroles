package pt.henrique.serverroles.command;

import pt.henrique.serverroles.ServerRoles;
import pt.henrique.serverroles.manager.PlayerRoleManager;
import pt.henrique.serverroles.manager.RoleManager;
import pt.henrique.serverroles.model.Role;
import pt.henrique.serverroles.model.RolePlayer;
import pt.henrique.serverroles.permission.PermissionAttachmentManager;
import pt.henrique.serverroles.temp.TempRoleManager;
import pt.henrique.serverroles.util.LangManager;
import pt.henrique.serverroles.util.MessageUtil;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Command handler for {@code /role} (aliases: {@code /roles}, {@code /sr}).
 * <p>
 * Registered via Paper's {@link io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager}
 * using the {@link BasicCommand} interface.  All player-facing text is sourced from the
 * active {@link LangManager} so responses are fully localised.
 */
@SuppressWarnings("UnstableApiUsage")
public class RoleCommand implements BasicCommand {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final ServerRoles plugin;
    private final RoleManager roleManager;
    private final PlayerRoleManager playerRoleManager;
    private final PermissionAttachmentManager permissionManager;
    private final TempRoleManager tempRoleManager;
    private final LangManager lang;

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "delete", "list", "info", "setprefix", "setcolor", "setop",
            "setpriority", "setparent", "addperm", "removeperm", "listperms",
            "assign", "assigntemp", "remove", "check", "reload", "papi"
    );

    public RoleCommand(ServerRoles plugin, RoleManager roleManager, PlayerRoleManager playerRoleManager,
                       PermissionAttachmentManager permissionManager, TempRoleManager tempRoleManager,
                       LangManager langManager) {
        this.plugin = plugin;
        this.roleManager = roleManager;
        this.playerRoleManager = playerRoleManager;
        this.permissionManager = permissionManager;
        this.tempRoleManager = tempRoleManager;
        this.lang = langManager;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!sender.hasPermission("serverroles.admin")) {
            sender.sendMessage(lang.get("no-permission"));
            return;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "create"     -> handleCreate(sender, args);
            case "delete"     -> handleDelete(sender, args);
            case "list"       -> handleList(sender);
            case "info"       -> handleInfo(sender, args);
            case "setprefix"  -> handleSetPrefix(sender, args);
            case "setcolor"   -> handleSetColor(sender, args);
            case "setop"      -> handleSetOp(sender, args);
            case "setpriority"-> handleSetPriority(sender, args);
            case "setparent"  -> handleSetParent(sender, args);
            case "addperm"    -> handleAddPerm(sender, args);
            case "removeperm" -> handleRemovePerm(sender, args);
            case "listperms"  -> handleListPerms(sender, args);
            case "assign"     -> handleAssign(sender, args);
            case "assigntemp" -> handleAssignTemp(sender, args);
            case "remove"     -> handleRemove(sender, args);
            case "check"      -> handleCheck(sender, args);
            case "reload"     -> handleReload(sender);
            case "papi"       -> handlePapi(sender);
            default           -> sendHelp(sender);
        }
    }

    // -------------------------------------------------------------------------
    // Sub-command handlers
    // -------------------------------------------------------------------------

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(lang.get("usage-create"));
            return;
        }

        String id          = args[1].toLowerCase();
        // Restrict role ids to a safe character set. Ids are used verbatim as
        // YAML path keys (roles.<id>.*); a '.' or whitespace would silently
        // corrupt the stored structure and lose the role on the next reload.
        if (!id.matches("[a-z0-9_-]+")) {
            sender.sendMessage(MessageUtil.parse(
                    "<color:#ff5555>Invalid role id. Use only lowercase letters, numbers, '_' or '-' (no dots or spaces)."));
            return;
        }
        String displayName = args[2];
        int    priority;
        try {
            priority = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(lang.get("priority-not-number"));
            return;
        }

        if (roleManager.roleExists(id)) {
            sender.sendMessage(lang.get("already-exists", Map.of("role", id)));
            return;
        }

        Role role = new Role(id, displayName, "", "", priority, false, "", new ArrayList<>());
        roleManager.createRole(role);
        sender.sendMessage(lang.get("role-created", Map.of("role", id)));
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(lang.get("usage-delete"));
            return;
        }

        String id = args[1].toLowerCase();
        if (id.equals(roleManager.getDefaultRoleId())) {
            sender.sendMessage(lang.get("cannot-delete-default"));
            return;
        }

        if (!roleManager.roleExists(id)) {
            sender.sendMessage(lang.get("role-not-found", Map.of("role", id)));
            return;
        }

        roleManager.deleteRole(id);
        sender.sendMessage(lang.get("role-deleted", Map.of("role", id)));
    }

    private void handleList(CommandSender sender) {
        List<Role> roles = roleManager.getAllRolesSorted();
        sender.sendMessage(MessageUtil.parse("<color:#ffaa00>--- All Roles (" + roles.size() + ") ---"));
        for (Role role : roles) {
            sender.sendMessage(MessageUtil.parse(
                    "<color:#aaaaaa>- " + MessageUtil.hexColorText(role.getColor(), role.getPrefix()) + " <color:#ffffff>" + role.getId() +
                    " <color:#aaaaaa>(priority: " + role.getPriority() + ", op: " + role.isOp() + ")"
            ));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(lang.get("usage-info"));
            return;
        }

        String id   = args[1].toLowerCase();
        Role   role = roleManager.getRole(id);
        if (role == null) {
            sender.sendMessage(lang.get("role-not-found", Map.of("role", id)));
            return;
        }

        sender.sendMessage(lang.get("role-info-header", Map.of("role", role.getDisplayName())));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>ID: <color:#ffffff>"          + role.getId()));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>Display Name: <color:#ffffff>" + role.getDisplayName()));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>Prefix: " + MessageUtil.hexColorText(role.getColor(), role.getPrefix())));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>Color: " + MessageUtil.hexColorText(role.getColor(), role.getColor() + " sample text")));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>Priority: <color:#ffffff>"    + role.getPriority()));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>Op: <color:#ffffff>"          + role.isOp()));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>Inherits From: <color:#ffffff>" +
                (role.getInheritFrom().isEmpty() ? "none" : role.getInheritFrom())));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>Permissions (" + role.getPermissions().size() + "):"));
        for (String perm : role.getPermissions()) {
            sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>  - <color:#ffffff>" + perm));
        }

        // Show effective permissions (including inherited).
        Map<String, Boolean> effective = roleManager.resolvePermissions(role);
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>Effective Permissions (" + effective.size() + "):"));
        for (Map.Entry<String, Boolean> entry : effective.entrySet()) {
            String color = entry.getValue() ? "<color:#55ff55>" : "<color:#ff5555>";
            sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>  - " + color + entry.getKey()));
        }
    }

    private void handleSetPrefix(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(lang.get("usage-setprefix"));
            return;
        }

        String id   = args[1].toLowerCase();
        Role   role = roleManager.getRole(id);
        if (role == null) {
            sender.sendMessage(lang.get("role-not-found", Map.of("role", id)));
            return;
        }

        // Allow multi-word prefixes (e.g. <red>[ADM]</red>).
        String prefix = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        role.setPrefix(prefix);
        roleManager.saveRole(role);
        permissionManager.recalculateAll();
        // "prefix" carries plugin-built MiniMessage (hex-coloured sample) → raw; "role" is escaped.
        sender.sendMessage(lang.get("prefix-set",
                Map.of("role", id, "prefix", MessageUtil.hexColorText(role.getColor(), prefix)),
                Set.of("prefix")));
    }

    private void handleSetColor(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(lang.get("usage-setcolor"));
            return;
        }

        String id   = args[1].toLowerCase();
        Role   role = roleManager.getRole(id);
        if (role == null) {
            sender.sendMessage(lang.get("role-not-found", Map.of("role", id)));
            return;
        }

        String color = args[2];
        // Normalise: ensure it starts with '#'
        if (!color.startsWith("#")) {
            color = "#" + color;
        }
        if (!color.matches("#[0-9a-fA-F]{6}")) {
            sender.sendMessage(MessageUtil.parse("<color:#ff5555>Invalid hex color. Use format: #rrggbb (e.g. #ff5555)"));
            return;
        }
        color = color.toLowerCase();
        role.setColor(color);
        roleManager.saveRole(role);
        // "color" carries a plugin-built MiniMessage colour tag → raw; "role" is escaped.
        sender.sendMessage(lang.get("color-set",
                Map.of("role", id, "color", MessageUtil.hexToMiniMessage(color)),
                Set.of("color")));
    }

    private void handleSetOp(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(lang.get("usage-setop"));
            return;
        }

        String id   = args[1].toLowerCase();
        Role   role = roleManager.getRole(id);
        if (role == null) {
            sender.sendMessage(lang.get("role-not-found", Map.of("role", id)));
            return;
        }

        boolean op = Boolean.parseBoolean(args[2]);
        role.setOp(op);
        roleManager.saveRole(role);
        permissionManager.recalculateAll();
        sender.sendMessage(lang.get("op-set", Map.of("role", id, "value", String.valueOf(op))));
    }

    private void handleSetPriority(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(lang.get("usage-setpriority"));
            return;
        }

        String id   = args[1].toLowerCase();
        Role   role = roleManager.getRole(id);
        if (role == null) {
            sender.sendMessage(lang.get("role-not-found", Map.of("role", id)));
            return;
        }

        int priority;
        try {
            priority = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(lang.get("priority-not-number-2"));
            return;
        }

        role.setPriority(priority);
        roleManager.saveRole(role);
        sender.sendMessage(lang.get("priority-set",
                Map.of("role", id, "value", String.valueOf(priority))));
    }

    private void handleSetParent(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(lang.get("usage-setparent"));
            return;
        }

        String id       = args[1].toLowerCase();
        Role   role     = roleManager.getRole(id);
        if (role == null) {
            sender.sendMessage(lang.get("role-not-found", Map.of("role", id)));
            return;
        }

        String parentId = args[2].toLowerCase();
        if (parentId.equals("none")) {
            role.setInheritFrom("");
            roleManager.saveRole(role);
            permissionManager.recalculateAll();
            sender.sendMessage(lang.get("parent-set", Map.of("role", id, "parent", "none")));
            return;
        }

        if (!roleManager.roleExists(parentId)) {
            sender.sendMessage(lang.get("role-not-found", Map.of("role", parentId)));
            return;
        }

        if (!roleManager.validateInheritance(id, parentId)) {
            sender.sendMessage(lang.get("circular-inheritance"));
            return;
        }

        role.setInheritFrom(parentId);
        roleManager.saveRole(role);
        permissionManager.recalculateAll();
        sender.sendMessage(lang.get("parent-set", Map.of("role", id, "parent", parentId)));
    }

    private void handleAddPerm(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(lang.get("usage-addperm"));
            return;
        }

        String id   = args[1].toLowerCase();
        Role   role = roleManager.getRole(id);
        if (role == null) {
            sender.sendMessage(lang.get("role-not-found", Map.of("role", id)));
            return;
        }

        String perm = args[2];
        // A comma would corrupt the CSV encoding used by the SQLite/MySQL backends
        // (permissions are stored as a comma-joined string and split on ','), and
        // no valid permission node contains one. Reject early with a clear message.
        if (perm.indexOf(',') >= 0 || perm.isBlank()) {
            sender.sendMessage(MessageUtil.parse(
                    "<color:#ff5555>Invalid permission node (must not be blank or contain a comma)."));
            return;
        }
        role.addPermission(perm);
        roleManager.saveRole(role);
        permissionManager.recalculateAll();
        sender.sendMessage(lang.get("permission-added", Map.of("perm", perm, "role", id)));
    }

    private void handleRemovePerm(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(lang.get("usage-removeperm"));
            return;
        }

        String id   = args[1].toLowerCase();
        Role   role = roleManager.getRole(id);
        if (role == null) {
            sender.sendMessage(lang.get("role-not-found", Map.of("role", id)));
            return;
        }

        String perm = args[2];
        role.removePermission(perm);
        roleManager.saveRole(role);
        permissionManager.recalculateAll();
        sender.sendMessage(lang.get("permission-removed", Map.of("perm", perm, "role", id)));
    }

    private void handleListPerms(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(lang.get("usage-listperms"));
            return;
        }

        String id   = args[1].toLowerCase();
        Role   role = roleManager.getRole(id);
        if (role == null) {
            sender.sendMessage(lang.get("role-not-found", Map.of("role", id)));
            return;
        }

        List<String> perms = role.getPermissions();
        sender.sendMessage(MessageUtil.parse(
                "<color:#ffaa00>--- Permissions for " + role.getDisplayName() +
                " (" + perms.size() + ") ---"));
        for (String perm : perms) {
            sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>  - <color:#ffffff>" + perm));
        }

        Map<String, Boolean> effective = roleManager.resolvePermissions(role);
        sender.sendMessage(MessageUtil.parse(
                "<color:#ffaa00>--- Effective (with inheritance: " + effective.size() + ") ---"));
        for (Map.Entry<String, Boolean> entry : effective.entrySet()) {
            String color = entry.getValue() ? "<color:#55ff55>" : "<color:#ff5555>";
            sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>  - " + color + entry.getKey()));
        }
    }

    private void handleAssign(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(lang.get("usage-assign"));
            return;
        }

        String playerName = args[1];
        String roleId     = args[2].toLowerCase();

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(lang.get("player-not-found", Map.of("player", playerName)));
            return;
        }

        if (!roleManager.roleExists(roleId)) {
            sender.sendMessage(lang.get("role-not-found", Map.of("role", roleId)));
            return;
        }

        boolean success = playerRoleManager.setPlayerRole(target.getUniqueId(), roleId);
        if (success) {
            permissionManager.recalculatePermissions(target);
            sender.sendMessage(lang.get("role-assigned",
                    Map.of("role", roleId, "player", target.getName())));
        } else {
            sender.sendMessage(lang.get("assign-failed"));
        }
    }

    private void handleAssignTemp(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(lang.get("usage-assigntemp"));
            return;
        }

        String playerName  = args[1];
        String roleId      = args[2].toLowerCase();
        String durationStr = args[3];

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(lang.get("player-not-found", Map.of("player", playerName)));
            return;
        }

        if (!roleManager.roleExists(roleId)) {
            sender.sendMessage(lang.get("role-not-found", Map.of("role", roleId)));
            return;
        }

        long durationMs = TempRoleManager.parseDuration(durationStr);
        if (durationMs <= 0) {
            sender.sendMessage(lang.get("invalid-duration"));
            return;
        }

        long    expiryTimestamp = System.currentTimeMillis() + durationMs;
        boolean success         = playerRoleManager.setPlayerRole(
                target.getUniqueId(), roleId, expiryTimestamp);
        if (success) {
            permissionManager.recalculatePermissions(target);
            sender.sendMessage(lang.get("temp-role-assigned",
                    Map.of("role", roleId, "player", target.getName(),
                           "duration", TempRoleManager.formatDuration(durationMs))));
        } else {
            sender.sendMessage(lang.get("assign-temp-failed"));
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(lang.get("usage-remove"));
            return;
        }

        String playerName = args[1];
        Player target     = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(lang.get("player-not-found", Map.of("player", playerName)));
            return;
        }

        boolean success = playerRoleManager.resetPlayerRole(target.getUniqueId());
        if (success) {
            permissionManager.recalculatePermissions(target);
            sender.sendMessage(lang.get("role-removed", Map.of("player", target.getName())));
        } else {
            sender.sendMessage(lang.get("remove-failed"));
        }
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(lang.get("usage-check"));
            return;
        }

        String playerName = args[1];
        Player target     = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(lang.get("player-not-found", Map.of("player", playerName)));
            return;
        }

        Role       role = playerRoleManager.getPlayerRole(target.getUniqueId());
        RolePlayer rp   = playerRoleManager.getPlayer(target.getUniqueId());

        sender.sendMessage(MessageUtil.parse("<color:#ffaa00>--- Player: " + target.getName() + " ---"));
        sender.sendMessage(MessageUtil.parse(
                "<color:#aaaaaa>Role: " + (role != null ? MessageUtil.hexColorText(role.getColor(), role.getPrefix()) + " " + role.getDisplayName() : "none")));
        sender.sendMessage(MessageUtil.parse(
                "<color:#aaaaaa>Role ID: <color:#ffffff>" + (role != null ? role.getId() : "none")));
        sender.sendMessage(MessageUtil.parse(
                "<color:#aaaaaa>Priority: <color:#ffffff>" + (role != null ? role.getPriority() : 0)));
        sender.sendMessage(MessageUtil.parse(
                "<color:#aaaaaa>Op: <color:#ffffff>" + (role != null && role.isOp())));

        if (rp != null && rp.isTemporary()) {
            sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>Temporary: <color:#ffff55>Yes"));
            synchronized (DATE_FORMAT) {
                sender.sendMessage(MessageUtil.parse(
                        "<color:#aaaaaa>Expires: <color:#ffff55>" + DATE_FORMAT.format(new Date(rp.getExpiryTimestamp()))));
            }
            sender.sendMessage(MessageUtil.parse(
                    "<color:#aaaaaa>Remaining: <color:#ffff55>" + TempRoleManager.formatRemaining(rp.getExpiryTimestamp())));
        } else {
            sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>Temporary: <color:#ffffff>No (permanent)"));
        }
    }

    private void handleReload(CommandSender sender) {
        // Schedule on the main thread so storage I/O and permission attachment
        // operations are always performed synchronously.
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                plugin.reloadConfig();

                // Re-load the (possibly changed) language.
                String language = plugin.getConfig().getString("language", "pt_PT");
                lang.load(language);

                roleManager.loadRoles();
                playerRoleManager.loadPlayers();
                permissionManager.recalculateAll();

                // Re-hook PAPI in case it was installed/reloaded since last startup.
                plugin.rehookPlaceholderAPI();

                sender.sendMessage(lang.get("reload-success"));
            } catch (Exception e) {
                sender.sendMessage(lang.get("reload-failed",
                        Map.of("error", String.valueOf(e.getMessage()))));
                plugin.getLogger().severe("Error during reload: " + e.getMessage());
            }
        });
    }

    private void handlePapi(CommandSender sender) {
        boolean papiInstalled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        boolean papiEnabled = papiInstalled
                && Bukkit.getPluginManager().getPlugin("PlaceholderAPI").isEnabled();
        boolean expansionRegistered = plugin.isPapiRegistered();

        sender.sendMessage(MessageUtil.parse("<color:#ffaa00>--- ServerRoles PAPI Diagnostics ---"));
        sender.sendMessage(MessageUtil.parse(
                "<color:#aaaaaa>PlaceholderAPI installed: " + boolColor(papiInstalled)));
        sender.sendMessage(MessageUtil.parse(
                "<color:#aaaaaa>PlaceholderAPI enabled:   " + boolColor(papiEnabled)));
        sender.sendMessage(MessageUtil.parse(
                "<color:#aaaaaa>Expansion registered:     " + boolColor(expansionRegistered)));
        sender.sendMessage(MessageUtil.parse(
                "<color:#aaaaaa>Identifier:               <color:#ffffff>serverroles"));

        // If sender is a player, resolve all placeholders for them.
        if (sender instanceof Player player) {
            sender.sendMessage(MessageUtil.parse("<color:#ffaa00>--- Placeholder Test (your values) ---"));

            Role role = playerRoleManager.getPlayerRole(player.getUniqueId());
            RolePlayer rp = playerRoleManager.getPlayer(player.getUniqueId());

            if (role != null) {
                sender.sendMessage(MessageUtil.parse(
                        "<color:#aaaaaa>role_id:          <color:#ffffff>" + role.getId()));
                sender.sendMessage(MessageUtil.parse(
                        "<color:#aaaaaa>role_name:        <color:#ffffff>" + role.getDisplayName()));
                sender.sendMessage(MessageUtil.parse(
                        "<color:#aaaaaa>prefix:           <color:#ffffff>" + role.getPrefix()));
                sender.sendMessage(MessageUtil.parse(
                        "<color:#aaaaaa>prefix_formatted: <color:#ffffff>" + MessageUtil.hexColorText(role.getColor(), role.getPrefix())));
                sender.sendMessage(MessageUtil.parse(
                        "<color:#aaaaaa>color:            <color:#ffffff>" + role.getColor()));
                sender.sendMessage(MessageUtil.parse(
                        "<color:#aaaaaa>priority:         <color:#ffffff>" + role.getPriority()));
                sender.sendMessage(MessageUtil.parse(
                        "<color:#aaaaaa>is_op:            <color:#ffffff>" + role.isOp()));
                sender.sendMessage(MessageUtil.parse(
                        "<color:#aaaaaa>is_temp:          <color:#ffffff>" + (rp != null && rp.isTemporary())));
                String expiry = (rp != null && rp.isTemporary())
                        ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(rp.getExpiryTimestamp()))
                        : "permanent";
                sender.sendMessage(MessageUtil.parse(
                        "<color:#aaaaaa>expiry:           <color:#ffffff>" + expiry));
            } else {
                sender.sendMessage(MessageUtil.parse(
                        "<color:#ff5555>Could not resolve your role."));
            }
        }

        if (!expansionRegistered) {
            sender.sendMessage(MessageUtil.parse(
                    "<color:#ffff55>Tip: <color:#ffffff>Expansion is not registered. "
                            + "Ensure PlaceholderAPI is installed and run <color:#ffaa00>/role reload<color:#ffffff>."));
        } else {
            sender.sendMessage(MessageUtil.parse(
                    "<color:#55ff55>Tip: <color:#ffffff>Test with: <color:#ffaa00>/papi parse me %serverroles_role_name%"));
        }
    }

    private static String boolColor(boolean value) {
        return value ? "<color:#55ff55>true" : "<color:#ff5555>false";
    }

    // -------------------------------------------------------------------------
    // Help
    // -------------------------------------------------------------------------

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageUtil.parse("<color:#ffaa00>--- ServerRoles Help ---"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role create <id> <displayName> <priority>"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role delete <id>"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role list"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role info <id>"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role setprefix <id> <prefix>"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role setcolor <id> <#rrggbb>"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role setop <id> <true|false>"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role setpriority <id> <number>"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role setparent <id> <parentId|none>"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role addperm <id> <permission>"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role removeperm <id> <permission>"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role listperms <id>"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role assign <player> <roleId>"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role assigntemp <player> <roleId> <duration>"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role remove <player>"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role check <player>"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role reload"));
        sender.sendMessage(MessageUtil.parse("<color:#aaaaaa>/role papi — PlaceholderAPI diagnostics"));
    }

    // -------------------------------------------------------------------------
    // Tab completion
    // -------------------------------------------------------------------------

    @Override
    @NotNull
    public Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!sender.hasPermission("serverroles.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterCompletions(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase();

        if (args.length == 2) {
            return switch (sub) {
                case "delete", "info", "setprefix", "setcolor", "setop", "setpriority",
                     "setparent", "addperm", "removeperm", "listperms" ->
                        filterCompletions(new ArrayList<>(roleManager.getAllRoleIds()), args[1]);
                case "assign", "assigntemp", "remove", "check" ->
                        filterCompletions(getOnlinePlayerNames(), args[1]);
                case "create" -> List.of("<id>");
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3) {
            return switch (sub) {
                case "assign", "assigntemp" ->
                        filterCompletions(new ArrayList<>(roleManager.getAllRoleIds()), args[2]);
                case "setop" -> filterCompletions(List.of("true", "false"), args[2]);
                case "setparent" -> {
                    List<String> options = new ArrayList<>(roleManager.getAllRoleIds());
                    options.add("none");
                    yield filterCompletions(options, args[2]);
                }
                case "setpriority" -> List.of("<number>");
                case "setprefix"   -> List.of("<prefix>");
                case "setcolor"    -> List.of("#rrggbb");
                case "create"      -> List.of("<displayName>");
                case "removeperm"  -> {
                    Role role = roleManager.getRole(args[1].toLowerCase());
                    yield role != null
                            ? filterCompletions(role.getPermissionsMutable(), args[2])
                            : Collections.emptyList();
                }
                default -> Collections.emptyList();
            };
        }

        if (args.length == 4) {
            return switch (sub) {
                case "assigntemp" -> List.of("1d", "7d", "24h", "30m", "1d12h");
                case "create"     -> List.of("<priority>");
                default           -> Collections.emptyList();
            };
        }

        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    private List<String> filterCompletions(List<String> options, String input) {
        String lower = input.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
