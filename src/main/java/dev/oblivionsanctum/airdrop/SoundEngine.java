package dev.oblivionsanctum.airdrop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lifecycle-driven sound system for airdrop crates.
 * Phases: spawn -> falling loop -> landing combo -> open combo.
 * Siren loop for high-tier active crates.
 */
public class SoundEngine {

    private final AirdropPlugin plugin;
    private final Map<UUID, Integer> fallingLoopTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> sirenTasks = new ConcurrentHashMap<>();

    public SoundEngine(AirdropPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Spawn cue (distant air-raid thunder, low pitch) ──────────────────

    public void playSpawnSound(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        ConfigManager cfg = plugin.getConfigManager();
        double radius = cfg.getSpawnSoundRadius();
        playToNearby(loc.getWorld(), loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 0.5f, radius);
    }

    /**
     * Plays global spawn cue sounds (world-wide and optionally server-wide).
     */
    public void playGlobalSpawnCue(Location loc, CrateTier tier) {
        if (loc == null || loc.getWorld() == null) return;
        ConfigManager cfg = plugin.getConfigManager();
        World world = loc.getWorld();
        int level = tier.getLevel();

        if (cfg.isSpawnWorldCueEnabled()) {
            float vol = cfg.getSpawnWorldCueVolume();
            for (Player p : world.getPlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, vol, 0.4f);
            }
        }

        if (cfg.isSpawnServerWideEnabled() && level >= cfg.getSpawnServerWideMinTier()) {
            float vol = cfg.getSpawnServerWideVolume();
            java.util.Set<String> excludeWorlds = new java.util.HashSet<>(cfg.getServerWideExcludeWorlds());
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(world)) continue;
                if (excludeWorlds.contains(p.getWorld().getName())) continue;
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, vol, 0.6f);
            }
        }
    }

    // ── Falling loop (steam hiss following the entity) ───────────────────

    public void startFallingLoop(CrateEntity crate) {
        UUID crateId = crate.getCrateId();
        if (fallingLoopTasks.containsKey(crateId)) return;

        int interval = plugin.getConfigManager().getFallLoopIntervalTicks();

        int taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Entity entity = crate.getFallingEntity();
            if (entity == null || !entity.isValid() || entity.isDead()) {
                stopFallingLoop(crate);
                return;
            }
            Location loc = entity.getLocation();
            if (loc.getWorld() == null) return;
            playToNearby(loc.getWorld(), loc, Sound.BLOCK_FIRE_AMBIENT, 0.8f, 1.2f, 120.0);
        }, 0, interval);

        fallingLoopTasks.put(crateId, taskId);
    }

    public void stopFallingLoop(CrateEntity crate) {
        Integer taskId = fallingLoopTasks.remove(crate.getCrateId());
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    // ── Landing combo (heavy thud + metal clank) ─────────────────────────

    public void playLandingSound(CrateTier tier, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        ConfigManager cfg = plugin.getConfigManager();
        double radius = cfg.getLandingSoundRadius();
        World world = loc.getWorld();
        int level = tier.getLevel();

        playToNearby(world, loc, Sound.ENTITY_IRON_GOLEM_DEATH, 0.9f, 0.6f, radius);
        playToNearby(world, loc, Sound.BLOCK_ANVIL_LAND, 0.7f, 0.8f, radius);

        if (level >= 5) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                playToNearby(world, loc, Sound.ENTITY_WITHER_SPAWN, 0.4f, 0.9f, radius), 4L);
        }
        if (level >= 7) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                playToNearby(world, loc, Sound.ENTITY_WITHER_DEATH, 0.8f, 0.7f, radius), 8L);
        }

        if (cfg.isLandingWorldCueEnabled() && level >= cfg.getLandingWorldMinTier()) {
            float vol = cfg.getLandingWorldVolume();
            for (Player p : world.getPlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, vol, 0.5f);
            }
        }

        if (cfg.isLandingServerWideEnabled() && level >= cfg.getLandingServerWideMinTier()) {
            float vol = cfg.getLandingServerWideVolume();
            java.util.Set<String> excludeWorlds = new java.util.HashSet<>(cfg.getServerWideExcludeWorlds());
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(world)) continue;
                if (excludeWorlds.contains(p.getWorld().getName())) continue;
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, vol, 0.7f);
            }
        }
    }

    // ── Open combo (mechanical piston + chest) ───────────────────────────

    public void playOpenSound(CrateTier tier, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        double radius = plugin.getConfigManager().getOpenSoundRadius();
        World world = loc.getWorld();

        playToNearby(world, loc, Sound.BLOCK_PISTON_EXTEND, 1.0f, 0.9f, radius);
        playToNearby(world, loc, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f, radius);

        if (tier.getLevel() >= 4 && tier.getLevel() <= 5) {
            playToNearby(world, loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.0f, radius);
        } else if (tier.getLevel() == 6) {
            playToNearby(world, loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.2f, radius);
        } else if (tier.getLevel() >= 7) {
            playToNearby(world, loc, Sound.BLOCK_END_PORTAL_SPAWN, 0.7f, 0.8f, radius);
        }
    }

    // ── Siren loop (high-tier active crates) ─────────────────────────────

    public void startSirenLoop(CrateEntity crate) {
        CrateTier tier = crate.getTier();
        if (!plugin.getConfigManager().isSirenTier(tier)) return;

        int interval = plugin.getConfigManager().getSirenInterval();
        Location loc = crate.getTargetLocation();
        UUID crateId = crate.getCrateId();

        Sound sound = tier.getLevel() >= 7 ? Sound.ENTITY_WITHER_SPAWN
                     : tier.getLevel() >= 5 ? Sound.BLOCK_NOTE_BLOCK_BASS
                     : Sound.BLOCK_ENDER_CHEST_OPEN;

        double radius = 200 + (tier.getLevel() - 1) * 40;

        int taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (loc.getWorld() == null) return;
            playToNearby(loc.getWorld(), loc, sound, 0.6f, 0.5f, radius);
        }, 0, interval);

        sirenTasks.put(crateId, taskId);
    }

    public void stopSirenLoop(CrateEntity crate) {
        Integer taskId = sirenTasks.remove(crate.getCrateId());
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    // ── Shutdown ─────────────────────────────────────────────────────────

    public void stopAll() {
        cancelAll(fallingLoopTasks);
        cancelAll(sirenTasks);
    }

    private void cancelAll(Map<UUID, Integer> tasks) {
        for (Integer taskId : tasks.values()) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
        tasks.clear();
    }

    // ── Utility ──────────────────────────────────────────────────────────

    private void playToNearby(World world, Location loc, Sound sound, float volume, float pitch, double radius) {
        double radiusSq = radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= radiusSq) {
                player.playSound(loc, sound, volume, pitch);
            }
        }
    }
}
