package dev.oblivionsanctum.airdrop;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Manages the lifecycle of a falling crate in the world.
 */
public class CrateEntity {

    private final AirdropPlugin plugin;
    private final UUID crateId;
    private final CrateTier tier;
    private final Location targetLocation;
    private final Location spawnLocation;
    private final boolean mystery;
    private Entity fallingEntity;
    private boolean landed;
    private final long spawnTime;
    private int trailTaskId;

    public CrateEntity(AirdropPlugin plugin, CrateTier tier, Location target, boolean mystery) {
        this.plugin = plugin;
        this.crateId = UUID.randomUUID();
        this.tier = tier;
        this.targetLocation = target.clone();
        this.mystery = mystery;
        this.landed = false;
        this.spawnTime = System.currentTimeMillis();
        this.trailTaskId = -1;

        int altitudeOffset = plugin.getConfigManager().getSpawnAltitudeOffset();
        this.spawnLocation = target.clone();
        this.spawnLocation.setY(target.getWorld().getHighestBlockYAt(target) + altitudeOffset);
    }

    /**
     * Spawns the falling block entity in the world.
     *
     * @return the spawned falling block entity
     */
    public Entity spawn() {
        FallingBlock fallingBlock = spawnLocation.getWorld().spawnFallingBlock(
                spawnLocation,
                Material.CHEST.createBlockData()
        );
        fallingBlock.setDropItem(false);
        fallingBlock.setHurtEntities(false);

        NamespacedKey idKey = new NamespacedKey(plugin, "airdrop_id");
        NamespacedKey tierKey = new NamespacedKey(plugin, "airdrop_tier");

        fallingBlock.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, crateId.toString());
        fallingBlock.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.name());

        this.fallingEntity = fallingBlock;
        return fallingBlock;
    }

    /**
     * Places the chest block at the target location when the crate lands.
     */
    public void land() {
        this.landed = true;

        Block block = targetLocation.getBlock();
        block.setType(Material.CHEST);

        if (block.getState() instanceof Chest chest) {
            String name = mystery
                    ? ChatColor.translateAlternateColorCodes('&', "&d&l[??? Crate]")
                    : tier.getColoredName();
            chest.setCustomName(name);
            chest.update();
        }
    }

    /**
     * Removes the falling entity if it is still valid.
     */
    public void remove() {
        if (fallingEntity != null && fallingEntity.isValid()) {
            fallingEntity.remove();
        }
    }

    public UUID getCrateId() {
        return crateId;
    }

    public CrateTier getTier() {
        return tier;
    }

    public Location getTargetLocation() {
        return targetLocation;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public boolean isMystery() {
        return mystery;
    }

    public Entity getFallingEntity() {
        return fallingEntity;
    }

    public boolean isLanded() {
        return landed;
    }

    public long getSpawnTime() {
        return spawnTime;
    }

    public int getTrailTaskId() {
        return trailTaskId;
    }

    public void setTrailTaskId(int trailTaskId) {
        this.trailTaskId = trailTaskId;
    }

    /**
     * Returns true if the crate has not landed and the current time exceeds spawnTime + timeout.
     */
    public boolean isExpired(long timeoutMs) {
        return !landed && System.currentTimeMillis() > spawnTime + timeoutMs;
    }
}
