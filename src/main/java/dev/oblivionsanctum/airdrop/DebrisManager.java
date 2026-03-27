package dev.oblivionsanctum.airdrop;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages debris placement and cleanup at airdrop landing sites.
 */
public class DebrisManager {

    private static final Material[] DEBRIS_BLOCKS = {
            Material.COBBLESTONE,
            Material.GRAVEL,
            Material.DEAD_BUSH
    };

    private static final Material[] DEBRIS_ITEMS = {
            Material.IRON_NUGGET,
            Material.COPPER_INGOT
    };

    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final AirdropPlugin plugin;
    private final List<Location> activeDebrisSites;
    private final Map<Location, List<Location>> debrisBlocks;
    private final Map<Location, List<ItemFrame>> debrisItemFrames;

    public DebrisManager(AirdropPlugin plugin) {
        this.plugin = plugin;
        this.activeDebrisSites = new ArrayList<>();
        this.debrisBlocks = new ConcurrentHashMap<>();
        this.debrisItemFrames = new ConcurrentHashMap<>();
    }

    /**
     * Places debris at the given location to simulate wreckage.
     *
     * @param location the center location for debris placement
     */
    public void placeDebris(Location location) {
        var configManager = plugin.getConfigManager();
        if (!configManager.isDebrisEnabled()) {
            return;
        }

        if (location == null || location.getWorld() == null) {
            return;
        }

        Location siteKey = location.clone();
        List<Location> placedBlocks = new ArrayList<>();
        List<ItemFrame> placedFrames = new ArrayList<>();

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int blockCount = random.nextInt(2, 5);

        for (int i = 0; i < blockCount; i++) {
            int dx = random.nextInt(-2, 3);
            int dz = random.nextInt(-2, 3);
            Block target = location.clone().add(dx, 0, dz).getBlock();
            Block top = target.getRelative(BlockFace.UP);

            if (top.getType().isAir() && target.getType().isSolid()) {
                Material debrisMaterial = DEBRIS_BLOCKS[random.nextInt(DEBRIS_BLOCKS.length)];
                top.setType(debrisMaterial);
                placedBlocks.add(top.getLocation().clone());
            }
        }

        int frameCount = random.nextInt(1, 3);
        for (int i = 0; i < frameCount; i++) {
            int dx = random.nextInt(-2, 3);
            int dz = random.nextInt(-2, 3);
            Block target = location.clone().add(dx, 0, dz).getBlock();
            BlockFace face = HORIZONTAL_FACES[random.nextInt(HORIZONTAL_FACES.length)];
            Block wall = target.getRelative(face);

            if (wall.getType().isSolid() && target.getType().isAir()) {
                try {
                    ItemFrame frame = location.getWorld().spawn(
                            target.getLocation().add(0.5, 0.5, 0.5),
                            ItemFrame.class,
                            f -> {
                                f.setFacingDirection(face);
                                Material itemMaterial = DEBRIS_ITEMS[random.nextInt(DEBRIS_ITEMS.length)];
                                f.setItem(new ItemStack(itemMaterial));
                            }
                    );
                    placedFrames.add(frame);
                } catch (Exception ignored) {
                    // Skip if placement fails
                }
            }
        }

        synchronized (activeDebrisSites) {
            activeDebrisSites.add(siteKey);
        }
        debrisBlocks.put(siteKey, placedBlocks);
        debrisItemFrames.put(siteKey, placedFrames);

        int durationSeconds = configManager.getDebrisDuration();
        scheduleCleanup(siteKey, durationSeconds);
    }

    /**
     * Schedules cleanup of debris at the given site after the specified duration.
     *
     * @param location       the debris site location
     * @param durationSeconds seconds until cleanup
     */
    public void scheduleCleanup(Location location, int durationSeconds) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        long delayTicks = durationSeconds * 20L;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> removeDebrisAt(location), delayTicks);
    }

    private void removeDebrisAt(Location siteKey) {
        List<Location> blocks = debrisBlocks.remove(siteKey);
        if (blocks != null) {
            for (Location blockLoc : blocks) {
                if (blockLoc.getWorld() != null) {
                    blockLoc.getBlock().setType(Material.AIR);
                }
            }
        }

        List<ItemFrame> frames = debrisItemFrames.remove(siteKey);
        if (frames != null) {
            for (ItemFrame frame : frames) {
                if (frame.isValid()) {
                    frame.remove();
                }
            }
        }

        synchronized (activeDebrisSites) {
            activeDebrisSites.removeIf(loc -> loc.equals(siteKey) ||
                    (loc.getWorld() != null && siteKey.getWorld() != null
                            && loc.getWorld().equals(siteKey.getWorld())
                            && loc.getBlockX() == siteKey.getBlockX()
                            && loc.getBlockY() == siteKey.getBlockY()
                            && loc.getBlockZ() == siteKey.getBlockZ()));
        }
    }

    /**
     * Removes all active debris immediately.
     */
    public void cleanupAll() {
        List<Location> sites;
        synchronized (activeDebrisSites) {
            sites = new ArrayList<>(activeDebrisSites);
        }
        for (Location site : sites) {
            removeDebrisAt(site);
        }
    }
}
