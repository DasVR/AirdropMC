package dev.oblivionsanctum.airdrop;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles visual effects for airdrop crates: trails, landing bursts, beacons, and active glow.
 */
public class VisualEffects {

    private static final double HELIX_RADIUS = 0.5;

    private final AirdropPlugin plugin;
    private final Map<UUID, Integer> glowTasks = new ConcurrentHashMap<>();

    public VisualEffects(AirdropPlugin plugin) {
        this.plugin = plugin;
    }

    private double density() {
        return plugin.getConfigManager().getParticleDensity();
    }

    private int scale(int base) {
        return Math.max(1, (int) (base * density()));
    }

    // ── Trail (while falling) ────────────────────────────────────────────

    public void startTrail(CrateEntity crate) {
        int interval = plugin.getConfigManager().getTrailIntervalTicks();
        int taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Entity entity = crate.getFallingEntity();
            if (entity == null || !entity.isValid() || entity.isDead()) {
                stopTrail(crate);
                return;
            }

            Location loc = entity.getLocation();
            World world = loc.getWorld();
            if (world == null) return;

            world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, loc, scale(4), 0.3, 0.2, 0.3, 0.01);
            world.spawnParticle(Particle.FLAME, loc, scale(3), 0.2, 0.1, 0.2, 0.02);

            int level = crate.getTier().getLevel();
            if (level >= 4 && level <= 5) {
                double angle = (System.currentTimeMillis() % 2000) / 2000.0 * Math.PI * 2;
                double x = Math.cos(angle) * HELIX_RADIUS;
                double z = Math.sin(angle) * HELIX_RADIUS;
                world.spawnParticle(Particle.ENCHANTMENT_TABLE, loc.clone().add(x, 0.3, z),
                        scale(2), 0.1, 0.1, 0.1, 0.5);
            } else if (level == 6) {
                world.spawnParticle(Particle.TOTEM, loc, scale(3), 0.3, 0.2, 0.3, 0.1);
            } else if (level >= 7) {
                world.spawnParticle(Particle.CRIMSON_SPORE, loc, scale(5), 0.4, 0.3, 0.4, 0.02);
                world.spawnParticle(Particle.SMOKE_LARGE, loc, scale(2), 0.2, 0.1, 0.2, 0.01);
            }
        }, 0, interval);

        crate.setTrailTaskId(taskId);
    }

    public void stopTrail(CrateEntity crate) {
        int taskId = crate.getTrailTaskId();
        if (taskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            crate.setTrailTaskId(-1);
        }
    }

    // ── Landing burst ────────────────────────────────────────────────────

    public void landingFX(CrateEntity crate) {
        Location loc = crate.getTargetLocation();
        World world = loc.getWorld();
        if (world == null) return;

        int level = crate.getTier().getLevel();

        world.spawnParticle(Particle.EXPLOSION_LARGE, loc, scale(3), 0.5, 0.3, 0.5, 0);
        world.spawnParticle(Particle.CRIT_MAGIC, loc, scale(30), 1.0, 0.5, 1.0, 0.3);
        world.spawnParticle(Particle.LAVA, loc, scale(12), 0.6, 0.3, 0.6, 0);

        if (level >= 4) {
            world.strikeLightningEffect(loc);
        }

        if (level >= 5) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                spawnRing(world, loc, 1.5, scale(8), Particle.CRIT_MAGIC);
                if (level >= 6) {
                    spawnFireworkBurst(loc);
                    world.spawnParticle(Particle.TOTEM, loc, scale(60), 1.0, 0.5, 1.0, 0.2);
                }
            }, 5L);
        }

        if (level >= 6) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                spawnRing(world, loc, 3.0, scale(12), Particle.ENCHANTMENT_TABLE);
                if (level >= 7) {
                    world.strikeLightningEffect(loc.clone().add(2, 0, 0));
                    world.strikeLightningEffect(loc.clone().add(-2, 0, 0));
                    world.strikeLightningEffect(loc.clone().add(0, 0, 2));
                    world.strikeLightningEffect(loc.clone().add(0, 0, -2));
                }
            }, 10L);
        }

        if (level >= 7) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (int y = 0; y < 6; y++) {
                    world.spawnParticle(Particle.SOUL, loc.clone().add(0, y * 0.5, 0),
                            scale(4), 0.3, 0.1, 0.3, 0.02);
                }
                world.spawnParticle(Particle.CRIMSON_SPORE, loc, scale(40), 1.0, 0.5, 1.0, 0.1);
                world.spawnParticle(Particle.SMOKE_LARGE, loc, scale(20), 0.8, 0.5, 0.8, 0.05);
            }, 15L);
        }
    }

    private void spawnRing(World world, Location center, double radius, int count, Particle particle) {
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            world.spawnParticle(particle, center.clone().add(x, 0.5, z), 2, 0.1, 0.2, 0.1, 0.1);
        }
    }

    private void spawnFireworkBurst(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        Firework firework = world.spawn(loc, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BURST)
                .withColor(Color.YELLOW, Color.ORANGE)
                .withFade(Color.WHITE)
                .withTrail()
                .withFlicker()
                .build());
        meta.setPower(0);
        firework.setFireworkMeta(meta);
        firework.detonate();
    }

    // ── Active glow (WITCH particles while unopened) ─────────────────────

    public void startActiveGlow(CrateEntity crate) {
        int interval = plugin.getConfigManager().getActiveGlowIntervalTicks();
        Location baseLoc = crate.getTargetLocation().clone().add(0.5, 0.8, 0.5);
        UUID crateId = crate.getCrateId();

        int taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            World world = baseLoc.getWorld();
            if (world == null) {
                stopActiveGlow(crateId);
                return;
            }
            world.spawnParticle(Particle.SPELL_WITCH, baseLoc, scale(6), 0.4, 0.3, 0.4, 0.02);
        }, 0, interval);

        glowTasks.put(crateId, taskId);
    }

    public void stopActiveGlow(UUID crateId) {
        Integer taskId = glowTasks.remove(crateId);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    // ── Beacon (vertical pillar above landed crate) ──────────────────────

    public void startBeacon(CrateEntity crate) {
        CrateTier tier = crate.getTier();
        if (tier == CrateTier.TIER1) return;

        int durationSeconds = Math.min(30 + tier.getLevel() * 20, 180);

        int interval = plugin.getConfigManager().getBeaconIntervalTicks();
        Location baseLoc = crate.getTargetLocation().clone().add(0.5, 1, 0.5);
        long endTime = System.currentTimeMillis() + durationSeconds * 1000L;

        plugin.getServer().getScheduler().runTaskTimer(plugin, (task) -> {
            if (System.currentTimeMillis() >= endTime) {
                task.cancel();
                return;
            }

            World world = baseLoc.getWorld();
            if (world == null) {
                task.cancel();
                return;
            }

            Particle particle = switch (tier) {
                case TIER1, TIER2, TIER3 -> Particle.FLAME;
                case TIER4, TIER5 -> Particle.ENCHANTMENT_TABLE;
                case TIER6 -> Particle.TOTEM;
                case TIER7 -> Particle.CRIMSON_SPORE;
                default -> Particle.FLAME;
            };

            for (int y = 0; y < 10; y++) {
                Location pillar = baseLoc.clone().add(0, y * 0.5, 0);
                world.spawnParticle(particle, pillar, scale(2), 0.2, 0.1, 0.2, 0.01);
            }
        }, 0, interval);
    }

    /**
     * Plays a one-shot spawn flash visible to all players in the same world.
     */
    public void playWorldSpawnFlash(Location loc, CrateTier tier) {
        if (loc == null || loc.getWorld() == null) return;
        World world = loc.getWorld();
        int level = tier.getLevel();

        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, loc.clone().add(0, 2, 0),
                scale(6), 0.5, 1.0, 0.5, 0.02);

        if (level >= 5) {
            spawnRing(world, loc.clone().add(0, 1, 0), 2.0, scale(10), Particle.CRIT_MAGIC);
        }
    }

    /**
     * Stops all active glow tasks. Called on plugin disable.
     */
    public void stopAllGlows() {
        for (Integer taskId : glowTasks.values()) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
        glowTasks.clear();
    }
}
