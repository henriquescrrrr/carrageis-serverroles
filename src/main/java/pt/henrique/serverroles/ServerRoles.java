package pt.henrique.serverroles;

import pt.henrique.serverroles.api.ServerRolesAPI;
import pt.henrique.serverroles.api.ServerRolesProvider;
import pt.henrique.serverroles.command.RoleCommand;
import pt.henrique.serverroles.listener.ChatFormatListener;
import pt.henrique.serverroles.listener.PlayerJoinListener;
import pt.henrique.serverroles.listener.PlayerQuitListener;
import pt.henrique.serverroles.manager.PlayerRoleManager;
import pt.henrique.serverroles.manager.RoleManager;
import pt.henrique.serverroles.model.Role;
import pt.henrique.serverroles.permission.PermissionAttachmentManager;
import pt.henrique.serverroles.storage.MySQLStorage;
import pt.henrique.serverroles.storage.SQLiteStorage;
import pt.henrique.serverroles.storage.StorageManager;
import pt.henrique.serverroles.storage.YamlStorage;
import pt.henrique.serverroles.temp.TempRoleManager;
import pt.henrique.serverroles.util.LangManager;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ServerRoles — a modern, lightweight role-based permission management plugin.
 * Main plugin class responsible for initialising all subsystems.
 */
public class ServerRoles extends JavaPlugin implements ServerRolesAPI {

    private LangManager langManager;
    private StorageManager storageManager;
    private RoleManager roleManager;
    private PlayerRoleManager playerRoleManager;
    private PermissionAttachmentManager permissionAttachmentManager;
    private TempRoleManager tempRoleManager;
    private boolean papiRegistered = false;

    @Override
    public void onEnable() {
        // Register permission nodes in code. paper-plugin.yml ignores any
        // `permissions:` block, so declaring them there alone would leave the
        // nodes unregistered (declared defaults never applied). Registering here
        // makes the defaults real and idempotent across reloads.
        registerPermissions();

        // Save default resource files (does NOT overwrite existing files).
        saveDefaultConfig();
        if (!new java.io.File(getDataFolder(), "roles.yml").exists()) {
            saveResource("roles.yml", false);
        }
        if (!new java.io.File(getDataFolder(), "players.yml").exists()) {
            saveResource("players.yml", false);
        }

        // Bootstrap the language manager first so subsequent components can use it.
        String language = getConfig().getString("language", "pt_PT");
        langManager = new LangManager(this);
        langManager.load(language);

        // Initialise storage backend.
        initStorage();

        // Initialise role & player managers.
        String defaultRoleId = getConfig().getString("default-role", "player");
        roleManager = new RoleManager(storageManager, getLogger(), defaultRoleId);
        roleManager.loadRoles();

        playerRoleManager = new PlayerRoleManager(storageManager, roleManager, getLogger());
        playerRoleManager.loadPlayers();

        permissionAttachmentManager = new PermissionAttachmentManager(
                this, roleManager, playerRoleManager, getLogger());

        tempRoleManager = new TempRoleManager(
                this, playerRoleManager, roleManager, permissionAttachmentManager,
                getLogger(), langManager);
        tempRoleManager.start();

        // Register Brigadier commands via Paper's LifecycleEventManager.
        registerCommands();

        // Register listeners.
        registerListeners();

        // Expose public API.
        ServerRolesProvider.register(this);

        // Hook into PlaceholderAPI if present.
        hookPlaceholderAPI();

        // Apply permissions to any already-online players (hot-reload scenario).
        permissionAttachmentManager.recalculateAll();

        getLogger().info("ServerRoles v" + getPluginMeta().getVersion() + " enabled successfully!");
    }

    /**
     * Re-hooks into PlaceholderAPI. Safe to call multiple times —
     * will skip if already registered or if PAPI is not present.
     * Called on startup and on {@code /role reload}.
     */
    public void rehookPlaceholderAPI() {
        hookPlaceholderAPI();
    }

    @Override
    public void onDisable() {
        if (tempRoleManager != null) {
            tempRoleManager.stop();
        }

        if (permissionAttachmentManager != null) {
            permissionAttachmentManager.cleanup();
        }

        if (playerRoleManager != null) {
            playerRoleManager.saveAllPlayers();
        }
        if (roleManager != null) {
            roleManager.saveAllRoles();
        }

        if (storageManager != null) {
            storageManager.shutdown();
        }

        ServerRolesProvider.unregister();
        getLogger().info("ServerRoles disabled.");
    }

    // -------------------------------------------------------------------------
    // Internal setup
    // -------------------------------------------------------------------------

    /**
     * Registers ServerRoles permission nodes at runtime.
     * <p>
     * Paper plugins declared via {@code paper-plugin.yml} do not process a
     * {@code permissions:} block, so the nodes must be registered here for their
     * declared defaults to take effect. Safe to call once on enable; guards
     * against double-registration (e.g. after a {@code /reload}).
     */
    private void registerPermissions() {
        registerPermission("serverroles.admin",
                "Allows access to all ServerRoles admin commands.",
                org.bukkit.permissions.PermissionDefault.OP);
        registerPermission("serverroles.user",
                "Basic ServerRoles user permission.",
                org.bukkit.permissions.PermissionDefault.TRUE);
    }

    private void registerPermission(String node, String description,
                                    org.bukkit.permissions.PermissionDefault def) {
        org.bukkit.plugin.PluginManager pm = getServer().getPluginManager();
        if (pm.getPermission(node) != null) {
            return; // already registered (server /reload re-enables the plugin)
        }
        pm.addPermission(new org.bukkit.permissions.Permission(node, description, def));
    }

    private void initStorage() {
        String type = getConfig().getString("storage.type", "yaml").toLowerCase();

        storageManager = switch (type) {
            case "sqlite" -> new SQLiteStorage(getDataFolder(), getLogger());
            case "mysql" -> {
                String host     = getConfig().getString("storage.mysql.host",     "localhost");
                int    port     = getConfig().getInt("storage.mysql.port",         3306);
                String database = getConfig().getString("storage.mysql.database",  "serverroles");
                String username = getConfig().getString("storage.mysql.username",  "root");
                String password = getConfig().getString("storage.mysql.password",  "password");
                yield new MySQLStorage(host, port, database, username, password, getLogger());
            }
            default -> {
                if (!type.equals("yaml")) {
                    getLogger().warning("Unknown storage type '" + type + "', falling back to YAML.");
                }
                yield new YamlStorage(getDataFolder(), getLogger());
            }
        };

        storageManager.init();
    }

    @SuppressWarnings("UnstableApiUsage")
    private void registerCommands() {
        RoleCommand roleCommand = new RoleCommand(
                this, roleManager, playerRoleManager,
                permissionAttachmentManager, tempRoleManager, langManager);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register("role", "ServerRoles main command", List.of("roles", "sr"), roleCommand);
        });
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(playerRoleManager, permissionAttachmentManager, tempRoleManager, getLogger()),
                this);
        getServer().getPluginManager().registerEvents(
                new PlayerQuitListener(playerRoleManager, permissionAttachmentManager, getLogger()),
                this);

        boolean chatFormat = getConfig().getBoolean("features.chat-format", true);
        getServer().getPluginManager().registerEvents(
                new ChatFormatListener(playerRoleManager, chatFormat),
                this);
    }

    private void hookPlaceholderAPI() {
        boolean debug = getConfig().getBoolean("debug.papi", false);
        org.bukkit.plugin.Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");

        if (debug) {
            getLogger().info("[PAPI Debug] PlaceholderAPI plugin reference: " + papi);
        }

        if (papi == null || !papi.isEnabled()) {
            getLogger().info("PlaceholderAPI not found; placeholders disabled.");
            papiRegistered = false;
            return;
        }

        getLogger().info("PlaceholderAPI detected, registering ServerRoles expansion...");

        try {
            // Use reflection to avoid loading the expansion class (and its PlaceholderAPI
            // parent) when PlaceholderAPI is not on the classpath. This prevents
            // NoClassDefFoundError when PAPI is absent.
            Class<?> expansionClass = Class.forName("pt.henrique.serverroles.placeholder.ServerRolesExpansion");
            Object expansion = expansionClass
                    .getConstructor(PlayerRoleManager.class, String.class)
                    .newInstance(playerRoleManager, getPluginMeta().getVersion());

            // PlaceholderExpansion.register() returns boolean
            Object result = expansionClass.getMethod("register").invoke(expansion);
            boolean registered = result instanceof Boolean b && b;

            if (debug) {
                getLogger().info("[PAPI Debug] Expansion class loaded: " + expansionClass.getName());
                getLogger().info("[PAPI Debug] register() returned: " + result);
            }

            if (registered) {
                papiRegistered = true;
                getLogger().info("ServerRoles PAPI expansion registered successfully (identifier: serverroles).");
            } else {
                papiRegistered = false;
                getLogger().warning("PlaceholderAPI expansion register() returned false. "
                        + "Placeholders will not work. Is another expansion using 'serverroles'?");
            }
        } catch (NoClassDefFoundError | Exception e) {
            papiRegistered = false;
            getLogger().warning("Failed to hook into PlaceholderAPI: " + e.getMessage());
            if (debug) {
                getLogger().warning("[PAPI Debug] Full stack trace:");
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns whether the PlaceholderAPI expansion was successfully registered.
     *
     * @return true if PAPI expansion is active
     */
    public boolean isPapiRegistered() {
        return papiRegistered;
    }

    // -------------------------------------------------------------------------
    // ServerRolesAPI implementation
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public Role getRoleById(String id) {
        return roleManager.getRole(id);
    }

    /** {@inheritDoc} */
    @Override
    public List<Role> getAllRoles() {
        return new ArrayList<>(roleManager.getAllRoles());
    }

    /** {@inheritDoc} */
    @Override
    public Role getPlayerRole(UUID uuid) {
        return playerRoleManager.getPlayerRole(uuid);
    }

    /** {@inheritDoc} */
    @Override
    public void setPlayerRole(UUID uuid, String roleId) {
        playerRoleManager.setPlayerRole(uuid, roleId);
        var player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            permissionAttachmentManager.recalculatePermissions(player);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setPlayerRoleTemp(UUID uuid, String roleId, long expiryTimestampMillis) {
        playerRoleManager.setPlayerRole(uuid, roleId, expiryTimestampMillis);
        var player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            permissionAttachmentManager.recalculatePermissions(player);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasPermission(UUID uuid, String permission) {
        return playerRoleManager.hasPermission(uuid, permission);
    }

    /** {@inheritDoc} */
    @Override
    public void registerRole(Role role) {
        roleManager.registerRole(role);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTemporary(UUID uuid) {
        return playerRoleManager.isTemporary(uuid);
    }

    /** {@inheritDoc} */
    @Override
    public long getExpiry(UUID uuid) {
        return playerRoleManager.getExpiry(uuid);
    }
}
