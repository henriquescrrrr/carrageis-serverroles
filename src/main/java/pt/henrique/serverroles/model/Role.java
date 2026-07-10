package pt.henrique.serverroles.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a role within the ServerRoles system.
 * Each role has an ID, display name, prefix, color, priority, op flag,
 * optional parent inheritance, and a list of permissions.
 */
public class Role {

    private final String id;
    private String displayName;
    private String prefix;
    private String color;
    private int priority;
    private boolean isOp;
    private String inheritFrom;
    private final List<String> permissions;

    /**
     * Constructs a new Role.
     *
     * @param id          the internal identifier (e.g. "admin")
     * @param displayName the human-readable name (e.g. "Admin")
     * @param prefix      the prefix text (e.g. "[ADM]")
     * @param color       the hex color (e.g. "#ff5555")
     * @param priority    the role priority/weight (higher = more powerful)
     * @param isOp        whether players with this role are granted operator status
     * @param inheritFrom the ID of the parent role to inherit from, or empty/null for none
     * @param permissions the list of permission nodes granted by this role
     */
    public Role(String id, String displayName, String prefix, String color,
                int priority, boolean isOp, String inheritFrom, List<String> permissions) {
        this.id = Objects.requireNonNull(id, "Role id must not be null");
        this.displayName = displayName != null ? displayName : id;
        this.prefix = prefix != null ? prefix : "";
        this.color = color != null ? color : "";
        this.priority = priority;
        this.isOp = isOp;
        this.inheritFrom = inheritFrom != null ? inheritFrom : "";
        this.permissions = permissions != null ? new ArrayList<>(permissions) : new ArrayList<>();
    }

    /** @return the internal role identifier */
    public String getId() {
        return id;
    }

    /** @return the human-readable display name */
    public String getDisplayName() {
        return displayName;
    }

    /** @param displayName the new display name */
    public void setDisplayName(String displayName) {
        this.displayName = displayName != null ? displayName : id;
    }

    /** @return the prefix text */
    public String getPrefix() {
        return prefix;
    }

    /** @param prefix the new prefix text */
    public void setPrefix(String prefix) {
        this.prefix = prefix != null ? prefix : "";
    }

    /** @return the hex color (e.g. "#ff5555") */
    public String getColor() {
        return color;
    }

    /** @param color the new hex color (e.g. "#ff5555") */
    public void setColor(String color) {
        this.color = color != null ? color : "";
    }

    /** @return the role priority (higher = more powerful) */
    public int getPriority() {
        return priority;
    }

    /** @param priority the new priority */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /** @return true if players with this role should be granted operator status */
    public boolean isOp() {
        return isOp;
    }

    /** @param op whether this role grants operator status */
    public void setOp(boolean op) {
        this.isOp = op;
    }

    /** @return the ID of the parent role to inherit from, or empty string if none */
    public String getInheritFrom() {
        return inheritFrom;
    }

    /** @param inheritFrom the parent role ID, or empty/null for no inheritance */
    public void setInheritFrom(String inheritFrom) {
        this.inheritFrom = inheritFrom != null ? inheritFrom : "";
    }

    /** @return an unmodifiable view of the permissions list */
    public List<String> getPermissions() {
        return Collections.unmodifiableList(permissions);
    }

    /**
     * Adds a permission to this role.
     *
     * @param permission the permission node to add
     */
    public void addPermission(String permission) {
        if (permission != null && !permission.isEmpty() && !permissions.contains(permission)) {
            permissions.add(permission);
        }
    }

    /**
     * Removes a permission from this role.
     *
     * @param permission the permission node to remove
     * @return true if the permission was present and removed
     */
    public boolean removePermission(String permission) {
        return permissions.remove(permission);
    }

    /**
     * @return a mutable copy of the permissions list (for storage purposes)
     */
    public List<String> getPermissionsMutable() {
        return new ArrayList<>(permissions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role role = (Role) o;
        return Objects.equals(id, role.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Role{id='" + id + "', displayName='" + displayName + "', priority=" + priority + "}";
    }
}

