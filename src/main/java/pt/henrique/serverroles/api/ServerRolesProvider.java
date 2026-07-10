package pt.henrique.serverroles.api;

/**
 * Static accessor for the ServerRoles API.
 * <p>
 * Usage:
 * <pre>
 *     ServerRolesAPI api = ServerRolesProvider.get();
 *     Role playerRole = api.getPlayerRole(player.getUniqueId());
 * </pre>
 */
public final class ServerRolesProvider {

    private static ServerRolesAPI instance;

    private ServerRolesProvider() {
        // utility class
    }

    /**
     * Gets the ServerRoles API instance.
     *
     * @return the API instance, or null if the plugin is not loaded
     */
    public static ServerRolesAPI get() {
        return instance;
    }

    /**
     * Registers the API implementation. Called internally by ServerRoles on enable.
     *
     * @param api the API implementation
     */
    public static void register(ServerRolesAPI api) {
        instance = api;
    }

    /**
     * Unregisters the API. Called internally by ServerRoles on disable.
     */
    public static void unregister() {
        instance = null;
    }
}

