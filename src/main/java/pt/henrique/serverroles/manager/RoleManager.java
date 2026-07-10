package pt.henrique.serverroles.manager;

import pt.henrique.serverroles.model.Role;
import pt.henrique.serverroles.storage.StorageManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages all roles in the system.
 * Handles loading, saving, creating, deleting, and querying roles.
 * Thread-safe via ConcurrentHashMap.
 */
public class RoleManager {

    private final StorageManager storage;
    private final Logger logger;
    private final String defaultRoleId;
    private final ConcurrentHashMap<String, Role> roles = new ConcurrentHashMap<>();

    public RoleManager(StorageManager storage, Logger logger, String defaultRoleId) {
        this.storage = storage;
        this.logger = logger;
        this.defaultRoleId = defaultRoleId != null ? defaultRoleId : "player";
    }

    /**
     * Loads all roles from storage. If no default role exists, creates one.
     */
    public void loadRoles() {
        roles.clear();
        Map<String, Role> loaded = storage.loadRoles();
        roles.putAll(loaded);

        // Ensure default role exists
        if (!roles.containsKey(defaultRoleId)) {
            Role defaultRole = new Role(defaultRoleId, "Player", "[PLAYER]",
                    "#aaaaaa", 0, false, "", new ArrayList<>());
            roles.put(defaultRoleId, defaultRole);
            storage.saveRole(defaultRole);
            logger.info("Created default role: " + defaultRoleId);
        }

        logger.info("Loaded " + roles.size() + " roles.");
    }

    /**
     * Returns a role by its ID.
     *
     * @param id the role ID
     * @return the Role, or null if not found
     */
    public Role getRole(String id) {
        if (id == null) return null;
        return roles.get(id.toLowerCase());
    }

    /**
     * Returns the default role.
     *
     * @return the default Role
     */
    public Role getDefaultRole() {
        return roles.get(defaultRoleId);
    }

    /** @return the default role ID */
    public String getDefaultRoleId() {
        return defaultRoleId;
    }

    /**
     * Returns all roles as an unmodifiable collection.
     *
     * @return all roles
     */
    public Collection<Role> getAllRoles() {
        return Collections.unmodifiableCollection(roles.values());
    }

    /**
     * Returns all role IDs.
     *
     * @return set of all role IDs
     */
    public Set<String> getAllRoleIds() {
        return Collections.unmodifiableSet(roles.keySet());
    }

    /**
     * Returns all roles sorted by priority descending.
     *
     * @return sorted list of roles
     */
    public List<Role> getAllRolesSorted() {
        return roles.values().stream()
                .sorted(Comparator.comparingInt(Role::getPriority).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Creates and registers a new role.
     *
     * @param role the role to register
     * @return true if created, false if a role with that ID already exists
     */
    public boolean createRole(Role role) {
        if (role == null || roles.containsKey(role.getId())) {
            return false;
        }
        roles.put(role.getId(), role);
        storage.saveRole(role);
        return true;
    }

    /**
     * Deletes a role by ID.
     *
     * @param roleId the role ID to delete
     * @return true if deleted, false if not found or is default
     */
    public boolean deleteRole(String roleId) {
        if (roleId == null || roleId.equals(defaultRoleId)) {
            return false;
        }
        Role removed = roles.remove(roleId);
        if (removed != null) {
            storage.deleteRole(roleId);
            return true;
        }
        return false;
    }

    /**
     * Saves a role to storage (after modifications).
     *
     * @param role the role to save
     */
    public void saveRole(Role role) {
        if (role != null) {
            storage.saveRole(role);
        }
    }

    /**
     * Saves all roles to storage.
     */
    public void saveAllRoles() {
        storage.saveAllRoles(new HashMap<>(roles));
    }

    /**
     * Checks if a role exists.
     *
     * @param roleId the role ID
     * @return true if the role exists
     */
    public boolean roleExists(String roleId) {
        return roleId != null && roles.containsKey(roleId.toLowerCase());
    }

    /**
     * Resolves all effective permissions for a role, including inherited ones.
     * Handles permission negation (prefixed with "-").
     * Prevents circular inheritance.
     *
     * @param role the role to resolve permissions for
     * @return a map of permission node to granted/denied (true/false)
     */
    public Map<String, Boolean> resolvePermissions(Role role) {
        Map<String, Boolean> permissions = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();
        resolvePermissionsRecursive(role, permissions, visited);
        return permissions;
    }

    private void resolvePermissionsRecursive(Role role, Map<String, Boolean> permissions, Set<String> visited) {
        if (role == null || visited.contains(role.getId())) {
            return; // prevent circular inheritance
        }
        visited.add(role.getId());

        // First resolve parent permissions (so child can override)
        String parentId = role.getInheritFrom();
        if (parentId != null && !parentId.isEmpty()) {
            Role parent = roles.get(parentId);
            if (parent != null) {
                resolvePermissionsRecursive(parent, permissions, visited);
            }
        }

        // Then apply this role's permissions (overriding parent if conflict)
        for (String perm : role.getPermissions()) {
            if (perm.startsWith("-")) {
                permissions.put(perm.substring(1), false);
            } else {
                permissions.put(perm, true);
            }
        }
    }

    /**
     * Validates that setting parentId as the parent of roleId would not create circular inheritance.
     *
     * @param roleId   the child role ID
     * @param parentId the proposed parent role ID
     * @return true if the inheritance is safe (no cycle), false if it would create a cycle
     */
    public boolean validateInheritance(String roleId, String parentId) {
        if (parentId == null || parentId.isEmpty() || parentId.equals("none")) {
            return true; // removing parent is always safe
        }
        if (roleId.equals(parentId)) {
            return false; // can't inherit from self
        }

        // Walk up the chain from parentId; if we reach roleId, it's circular
        Set<String> visited = new HashSet<>();
        String current = parentId;
        while (current != null && !current.isEmpty()) {
            if (current.equals(roleId)) {
                return false; // circular!
            }
            if (visited.contains(current)) {
                break; // existing cycle in the chain (shouldn't happen but be safe)
            }
            visited.add(current);
            Role currentRole = roles.get(current);
            if (currentRole == null) {
                break;
            }
            current = currentRole.getInheritFrom();
        }
        return true;
    }

    /**
     * Registers a role into the runtime cache (for API use).
     *
     * @param role the role to register
     */
    public void registerRole(Role role) {
        if (role != null) {
            roles.put(role.getId(), role);
            storage.saveRole(role);
        }
    }
}

