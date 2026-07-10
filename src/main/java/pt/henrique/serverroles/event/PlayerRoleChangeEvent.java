package pt.henrique.serverroles.event;

import pt.henrique.serverroles.model.Role;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player's role is about to change.
 * This event is cancellable — cancelling it prevents the role change.
 */
public class PlayerRoleChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private final Role oldRole;
    private final Role newRole;
    private final boolean temporary;
    private final long expiryTimestamp;
    private boolean cancelled;

    /**
     * Constructs a new PlayerRoleChangeEvent.
     *
     * @param player          the player whose role is changing
     * @param oldRole         the player's current (old) role
     * @param newRole         the player's new role
     * @param temporary       whether the new assignment is temporary
     * @param expiryTimestamp  the expiry timestamp in milliseconds, or -1 if permanent
     */
    public PlayerRoleChangeEvent(@NotNull Player player, @Nullable Role oldRole,
                                 @NotNull Role newRole, boolean temporary, long expiryTimestamp) {
        super(false); // not async
        this.player = player;
        this.oldRole = oldRole;
        this.newRole = newRole;
        this.temporary = temporary;
        this.expiryTimestamp = expiryTimestamp;
        this.cancelled = false;
    }

    /** @return the player whose role is changing */
    @NotNull
    public Player getPlayer() {
        return player;
    }

    /** @return the player's previous role, or null if none */
    @Nullable
    public Role getOldRole() {
        return oldRole;
    }

    /** @return the new role being assigned */
    @NotNull
    public Role getNewRole() {
        return newRole;
    }

    /** @return true if this is a temporary role assignment */
    public boolean isTemporary() {
        return temporary;
    }

    /** @return the expiry timestamp in milliseconds, or -1 if permanent */
    public long getExpiryTimestamp() {
        return expiryTimestamp;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}

