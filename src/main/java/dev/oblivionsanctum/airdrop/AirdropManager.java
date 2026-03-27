package dev.oblivionsanctum.airdrop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Central orchestrator for spawning and managing airdrops.
 */
public class AirdropManager {

    private static final int MAX_TIER_REROLL_ATTEMPTS = 5;
    private static final int FALLBACK_RADIUS = 1000;

    private final AirdropPlugin plugin;
    private final ConfigManager configManager;
    private final Map<UUID, CrateEntity> activeCrates;
    private final Map<CrateTier, Long> tierCooldowns;
    private volatile CrateEntity currentActiveCrate;

    public AirdropManager(AirdropPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.activeCrates = new ConcurrentHashMap<>();
        this.tierCooldowns = new EnumMap<>(CrateTier.class);
        this.currentActiveCrate = null;
    }

    /**
     * Triggers an automatic airdrop: rolls tier, picks location, and spawns.
     */
    public void triggerAuto() {
        Map<CrateTier, Integer> weights = configManager.getTierWeights();
        if (weights == null || weights.isEmpty()) {
            return;
        }

        CrateTier tier = rollTier(weights);
        for (int attempt = 0; attempt < MAX_TIER_REROLL_ATTEMPTS; attempt++) {
            long lastDrop = tierCooldowns.getOrDefault(tier, 0L);
            int cooldownSec = configManager.getTierCooldown(tier);
            long cooldownMs = cooldownSec * 1000L;
            if (cooldownMs <= 0 || (System.currentTimeMillis() - lastDrop) >= cooldownMs) {
                break;
            }
            tier = rollTier(weights);
            if (attempt == MAX_TIER_REROLL_ATTEMPTS - 1) {
                tier = CrateTier.TIER1;
            }
        }

        Location location = null;
        var zoneManager = plugin.getZoneManager();
        String locationMode = configManager.getLocationMode();

        if ("zones".equalsIgnoreCase(locationMode)) {
            if (zoneManager != null) location = zoneManager.pickDropLocation();
        } else if ("world-border".equalsIgnoreCase(locationMode)) {
            if (zoneManager != null) {
                if (configManager.isPlayerBiasEnabled()) {
                    location = zoneManager.pickPlayerBiasedWorldBorderLocation();
                }
                if (location == null) {
                    location = zoneManager.pickWorldBorderLocation();
                }
            }
        } else if ("both".equalsIgnoreCase(locationMode)) {
            if (zoneManager != null) location = zoneManager.pickDropLocation();
            if (location == null && zoneManager != null) {
                if (configManager.isPlayerBiasEnabled()) {
                    location = zoneManager.pickPlayerBiasedWorldBorderLocation();
                }
                if (location == null) {
                    location = zoneManager.pickWorldBorderLocation();
                }
            }
        }

        if (location == null && configManager.isFallbackRandomCoords()) {
            location = generateRandomFallbackLocation();
        }

        if (location == null) {
            return;
        }

        boolean mystery = ThreadLocalRandom.current().nextDouble() < configManager.getMysteryChance();
        spawnAt(tier, location, mystery, AirdropEvent.TriggerSource.AUTO);
    }

    /**
     * Spawns a crate at the given location.
     *
     * @return the spawned CrateEntity, or null if the event was cancelled
     */
    public CrateEntity spawnAt(CrateTier tier, Location location, boolean mystery,
                               AirdropEvent.TriggerSource source) {
        if (tier == null || location == null || location.getWorld() == null) {
            return null;
        }

        AirdropEvent event = new AirdropEvent(tier, location, mystery, source);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return null;
        }

        CrateEntity crateEntity = new CrateEntity(plugin, tier, location, mystery);
        crateEntity.spawn();

        activeCrates.put(crateEntity.getCrateId(), crateEntity);
        currentActiveCrate = crateEntity;

        tierCooldowns.put(tier, System.currentTimeMillis());

        String msg;
        if (mystery) {
            msg = configManager.getMysteryFormat();
        } else {
            msg = configManager.getAnnounceFormat(tier)
                    .replace("{x}", String.valueOf(location.getBlockX()))
                    .replace("{z}", String.valueOf(location.getBlockZ()));
        }
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));

        if (configManager.isDiscordEnabled()) {
            var discordHook = plugin.getDiscordHook();
            if (discordHook != null) {
                discordHook.fireTierLand(crateEntity);
            }
        }

        var visualEffects = plugin.getVisualEffects();
        if (visualEffects != null) {
            visualEffects.startTrail(crateEntity);
            visualEffects.playWorldSpawnFlash(crateEntity.getSpawnLocation(), tier);
        }

        var soundEngine = plugin.getSoundEngine();
        if (soundEngine != null) {
            soundEngine.playSpawnSound(crateEntity.getSpawnLocation());
            soundEngine.playGlobalSpawnCue(crateEntity.getSpawnLocation(), tier);
            soundEngine.startFallingLoop(crateEntity);
        }

        var raceTracker = plugin.getRaceTracker();
        if (raceTracker != null) {
            raceTracker.startTracking(crateEntity);
        }

        if (configManager.isLogDrops()) {
            logDrop(crateEntity);
        }

        return crateEntity;
    }

    /**
     * Weighted random selection of a CrateTier.
     */
    public CrateTier rollTier(Map<CrateTier, Integer> weights) {
        if (weights == null || weights.isEmpty()) {
            return CrateTier.TIER1;
        }

        int total = 0;
        for (Integer w : weights.values()) {
            total += (w != null ? w : 0);
        }
        if (total <= 0) {
            return CrateTier.TIER1;
        }

        int roll = ThreadLocalRandom.current().nextInt(total);
        int accumulated = 0;
        for (Map.Entry<CrateTier, Integer> e : weights.entrySet()) {
            accumulated += (e.getValue() != null ? e.getValue() : 0);
            if (roll < accumulated) {
                return e.getKey();
            }
        }
        return CrateTier.TIER1;
    }

    /**
     * Handles crate landing: visual effects, sounds, siren, defence, heatmap.
     */
    public void handleCrateLand(CrateEntity crate) {
        if (crate == null) return;

        crate.land();

        var visualEffects = plugin.getVisualEffects();
        if (visualEffects != null) {
            visualEffects.stopTrail(crate);
            visualEffects.landingFX(crate);
            visualEffects.startActiveGlow(crate);
            visualEffects.startBeacon(crate);
        }

        var soundEngine = plugin.getSoundEngine();
        if (soundEngine != null) {
            soundEngine.stopFallingLoop(crate);
            soundEngine.playLandingSound(crate.getTier(), crate.getTargetLocation());
        }

        if (configManager.isSirenTier(crate.getTier()) && soundEngine != null) {
            soundEngine.startSirenLoop(crate);
        }

        if (configManager.isCrateDefenceTier(crate.getTier())) {
            var eventDropManager = plugin.getEventDropManager();
            if (eventDropManager != null) {
                eventDropManager.spawnCrateDefence(crate);
            }
        }

        var heatmapTracker = plugin.getHeatmapTracker();
        if (heatmapTracker != null) {
            heatmapTracker.recordDrop(crate.getTargetLocation());
        }
    }

    /**
     * Handles crate opening: key validation, loot generation, claim resolution, cleanup.
     */
    public void handleCrateOpen(CrateEntity crate, Player opener) {
        if (crate == null || opener == null) return;

        var keyManager = plugin.getKeyManager();
        if (keyManager != null && configManager.isTierKeyed(crate.getTier())) {
            if (!keyManager.validateKey(opener, crate.getTier())) {
                return;
            }
        }

        var lootTableLoader = plugin.getLootTableLoader();
        if (lootTableLoader == null) return;

        var lootTable = lootTableLoader.getLootTable(crate.getTier());
        if (lootTable == null) return;

        boolean seasonalActive = false;
        var eventDropManager = plugin.getEventDropManager();
        if (eventDropManager != null) {
            seasonalActive = eventDropManager.isSeasonalActive();
        }

        var loot = lootTable.generate(crate.isMystery(), seasonalActive);

        var claimManager = plugin.getClaimManager();
        if (claimManager != null) {
            claimManager.resolveClaim(crate, opener, loot);
        }

        var raceTracker = plugin.getRaceTracker();
        if (raceTracker != null) {
            raceTracker.markClaimed(crate, opener);
            raceTracker.awardSalvagerBonus(crate, opener);
        }

        var leaderboardManager = plugin.getLeaderboardManager();
        if (leaderboardManager != null) {
            leaderboardManager.recordOpen(opener, crate.getTier());
        }

        var visualEffects = plugin.getVisualEffects();
        if (visualEffects != null) {
            visualEffects.stopActiveGlow(crate.getCrateId());
        }

        var soundEngine = plugin.getSoundEngine();
        if (soundEngine != null) {
            soundEngine.playOpenSound(crate.getTier(), crate.getTargetLocation());
            soundEngine.stopSirenLoop(crate);
        }

        if (configManager.isDiscordEnabled()) {
            var discordHook = plugin.getDiscordHook();
            if (discordHook != null) {
                discordHook.fireTierClaim(crate, opener, List.of());
            }
        }

        String claimedMsg = configManager.getClaimedFormat()
                .replace("{player}", opener.getName())
                .replace("{tier}", crate.getTier().getDisplayName());
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', claimedMsg));

        var debrisManager = plugin.getDebrisManager();
        if (debrisManager != null) {
            debrisManager.placeDebris(crate.getTargetLocation());
        }

        var eventDropMgr = plugin.getEventDropManager();
        if (eventDropMgr != null) {
            eventDropMgr.despawnCrateDefence(crate);
        }

        activeCrates.remove(crate.getCrateId());
        if (currentActiveCrate != null && currentActiveCrate.getCrateId().equals(crate.getCrateId())) {
            currentActiveCrate = null;
        }

        Block block = crate.getTargetLocation().getBlock();
        block.setType(org.bukkit.Material.AIR);
    }

    public boolean hasActiveDrop() {
        return currentActiveCrate != null;
    }

    public String getActiveTierName() {
        return currentActiveCrate != null ? currentActiveCrate.getTier().getDisplayName() : "";
    }

    public String getActiveCoordsString() {
        if (currentActiveCrate == null) return "";
        Location loc = currentActiveCrate.getTargetLocation();
        return loc.getBlockX() + ", " + loc.getBlockZ();
    }

    public CrateEntity getActiveCrate() {
        return currentActiveCrate;
    }

    public Map<UUID, CrateEntity> getActiveCrates() {
        return activeCrates;
    }

    /**
     * Removes a crate from the active map by ID.
     */
    public void removeCrate(UUID id) {
        CrateEntity removed = activeCrates.remove(id);
        if (removed != null && currentActiveCrate != null
                && currentActiveCrate.getCrateId().equals(id)) {
            currentActiveCrate = null;
        }
    }

    /**
     * Force-despawns a specific airdrop crate by UUID.
     *
     * @return true if a crate was found and removed
     */
    public boolean despawnCrate(UUID id) {
        if (id == null) return false;
        CrateEntity crate = activeCrates.get(id);
        if (crate == null) return false;
        cleanupCrate(crate);
        removeCrate(id);
        return true;
    }

    /**
     * Force-despawns all active airdrop crates.
     *
     * @return number of crates removed
     */
    public int despawnAllCrates() {
        int removed = 0;
        for (CrateEntity crate : List.copyOf(activeCrates.values())) {
            if (crate == null) continue;
            cleanupCrate(crate);
            removeCrate(crate.getCrateId());
            removed++;
        }
        return removed;
    }

    /**
     * Finds the nearest active crate to a player (same world).
     */
    public CrateEntity findNearestCrate(Player player) {
        if (player == null || player.getWorld() == null) return null;
        Location origin = player.getLocation();
        CrateEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (CrateEntity crate : activeCrates.values()) {
            if (crate == null) continue;
            Location loc = crate.getTargetLocation();
            if (loc == null || loc.getWorld() == null) continue;
            if (!loc.getWorld().equals(player.getWorld())) continue;
            double d = origin.distanceSquared(loc);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = crate;
            }
        }
        return best;
    }

    private void cleanupCrate(CrateEntity crate) {
        if (crate == null) return;

        var raceTracker = plugin.getRaceTracker();
        if (raceTracker != null) {
            raceTracker.stopTracking(crate);
        }

        var visualEffects = plugin.getVisualEffects();
        if (visualEffects != null) {
            visualEffects.stopTrail(crate);
            visualEffects.stopActiveGlow(crate.getCrateId());
        }

        var soundEngine = plugin.getSoundEngine();
        if (soundEngine != null) {
            soundEngine.stopFallingLoop(crate);
            soundEngine.stopSirenLoop(crate);
        }

        var eventDropMgr = plugin.getEventDropManager();
        if (eventDropMgr != null) {
            eventDropMgr.despawnCrateDefence(crate);
        }

        // Remove falling entity if still present
        crate.remove();

        // Remove the chest block if it was placed
        Location target = crate.getTargetLocation();
        if (target != null && target.getWorld() != null) {
            Block block = target.getBlock();
            if (block.getType() == org.bukkit.Material.CHEST) {
                block.setType(org.bukkit.Material.AIR);
            }
        }
    }

    private Location generateRandomFallbackLocation() {
        World world = Bukkit.getWorld(configManager.getWorldName());
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) return null;

        int maxRadius = configManager.getWorldBorderMaxRadius();
        int radius = maxRadius > 0 ? Math.min(FALLBACK_RADIUS, maxRadius) : FALLBACK_RADIUS;

        Location spawn = world.getSpawnLocation();
        for (int attempt = 0; attempt < 20; attempt++) {
            int x = spawn.getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int z = spawn.getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int y = world.getHighestBlockYAt(x, z);
            org.bukkit.Material surface = world.getBlockAt(x, y - 1, z).getType();
            if (surface == org.bukkit.Material.WATER || surface == org.bukkit.Material.LAVA || !surface.isSolid()) {
                continue;
            }
            return new Location(world, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    private void logDrop(CrateEntity crate) {
        // Basic logging; can be extended to write to configManager.getLogFile()
        plugin.getLogger().info(String.format("Airdrop spawned: %s at %s (mystery=%s)",
                crate.getTier().getDisplayName(),
                formatLocation(crate.getTargetLocation()),
                crate.isMystery()));
    }

    private static String formatLocation(Location loc) {
        if (loc == null) return "null";
        return String.format("%s %d, %d, %d",
                loc.getWorld() != null ? loc.getWorld().getName() : "?",
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
