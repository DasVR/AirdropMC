package dev.oblivionsanctum.airdrop;

import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Custom cancellable Bukkit event fired when an airdrop is triggered.
 */
public class AirdropEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CrateTier tier;
    private final Location targetLocation;
    private final boolean mystery;
    private final TriggerSource source;
    private boolean cancelled;

    public AirdropEvent(CrateTier tier, Location targetLocation, boolean mystery, TriggerSource source) {
        this.tier = tier;
        this.targetLocation = targetLocation;
        this.mystery = mystery;
        this.source = source;
        this.cancelled = false;
    }

    public CrateTier getTier() {
        return tier;
    }

    public Location getTargetLocation() {
        return targetLocation;
    }

    public boolean isMystery() {
        return mystery;
    }

    public TriggerSource getSource() {
        return source;
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
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Source that triggered the airdrop.
     */
    public enum TriggerSource {
        AUTO,
        FLARE,
        COMMAND,
        EVENT
    }
}
