package dev.oblivionsanctum.airdrop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages event-based airdrops: double drop, eclipse, drop surge, seasonal windows, and crate defence.
 */
public class EventDropManager {

    private static final int DEFENCE_DESPAWN_TICKS = 20 * 60 * 5; // 5 minutes
    private static final int DROP_SURGE_GAP_TICKS = 20 * 60 * 3;   // 3 minutes

    private final AirdropPlugin plugin;
    private final ConfigManager configManager;

    private boolean doubleDropActive;
    private long doubleDropEnd;
    private int doubleDropTaskId = -1;

    private boolean seasonalActive;
    private String activeEventName;

    private final NamespacedKey guardKey;
    private final Map<UUID, Integer> behaviourTaskIds = new HashMap<>();

    public EventDropManager(AirdropPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.doubleDropActive = false;
        this.doubleDropEnd = 0L;
        this.seasonalActive = false;
        this.activeEventName = null;
        this.guardKey = new NamespacedKey(plugin, "airdrop_guard");
    }

    public void startDoubleDrop(int durationMinutes) {
        stopDoubleDrop();

        doubleDropActive = true;
        activeEventName = "Double Drop";
        doubleDropEnd = System.currentTimeMillis() + durationMinutes * 60000L;

        var scheduler = plugin.getAirdropScheduler();
        if (scheduler != null) {
            scheduler.setDoubleDropActive(true);
        }

        String msg = ChatColor.translateAlternateColorCodes('&',
                "&6&l[DOUBLE DROP] Airdrops now falling twice as fast for " + durationMinutes + " minutes!");
        Bukkit.broadcastMessage(msg);

        long delayTicks = durationMinutes * 60L * 20L;
        doubleDropTaskId = Bukkit.getScheduler().runTaskLater(plugin, this::stopDoubleDrop, delayTicks).getTaskId();

        if (configManager.isDiscordEnabled()) {
            var discordHook = plugin.getDiscordHook();
            if (discordHook != null) {
                discordHook.fireDoubleDrop(durationMinutes);
            }
        }
    }

    public void stopDoubleDrop() {
        doubleDropActive = false;
        activeEventName = null;

        var scheduler = plugin.getAirdropScheduler();
        if (scheduler != null) {
            scheduler.setDoubleDropActive(false);
        }

        String msg = ChatColor.translateAlternateColorCodes('&', "&6[DOUBLE DROP] The surge has ended.");
        Bukkit.broadcastMessage(msg);

        if (doubleDropTaskId != -1) {
            Bukkit.getScheduler().cancelTask(doubleDropTaskId);
            doubleDropTaskId = -1;
        }
    }

    public void triggerEclipse(Location location) {
        if (location == null) {
            var zoneManager = plugin.getZoneManager();
            if (zoneManager != null) {
                location = zoneManager.pickDropLocation();
            }
        }

        if (location == null) {
            World world = Bukkit.getWorld(configManager.getWorldName());
            if (world != null) {
                location = world.getSpawnLocation();
            }
        }

        if (location == null) return;

        String msg = ChatColor.translateAlternateColorCodes('&', "&4&l[ECLIPSE] The void tears open. Something falls...");
        Bukkit.broadcastMessage(msg);

        if (configManager.isDiscordEnabled()) {
            var discordHook = plugin.getDiscordHook();
            if (discordHook != null) {
                discordHook.fireEclipse(location);
            }
        }

        var airdropManager = plugin.getAirdropManager();
        if (airdropManager != null) {
            airdropManager.spawnAt(CrateTier.TIER7, location, false, AirdropEvent.TriggerSource.EVENT);
        }
    }

    public void triggerDropSurge() {
        String msg = ChatColor.translateAlternateColorCodes('&', "&6[DROP SURGE] Three crates incoming!");
        Bukkit.broadcastMessage(msg);

        var airdropManager = plugin.getAirdropManager();
        if (airdropManager == null) return;

        for (int i = 0; i < 3; i++) {
            final int delay = i * DROP_SURGE_GAP_TICKS;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                long startNs = System.nanoTime();
                CrateTier tier = ThreadLocalRandom.current().nextBoolean() ? CrateTier.TIER1 : CrateTier.TIER2;
                Location loc = null;
                var zoneManager = plugin.getZoneManager();
                if (zoneManager != null) {
                    loc = zoneManager.pickDropLocation();
                }
                if (loc == null && configManager.isFallbackRandomCoords()) {
                    World world = Bukkit.getWorld(configManager.getWorldName());
                    if (world != null) {
                        int x = world.getSpawnLocation().getBlockX() + ThreadLocalRandom.current().nextInt(-500, 501);
                        int z = world.getSpawnLocation().getBlockZ() + ThreadLocalRandom.current().nextInt(-500, 501);
                        int y = world.getHighestBlockYAt(x, z);
                        loc = new Location(world, x + 0.5, y, z + 0.5);
                    }
                }
                if (loc != null) {
                    airdropManager.spawnAt(tier, loc, false, AirdropEvent.TriggerSource.EVENT);
                }
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
                // #region agent log
                DebugLogger.log(
                        "review-run-1",
                        "H3",
                        "EventDropManager.java:triggerDropSurge",
                        "Drop surge spawn tick",
                        new LinkedHashMap<>(Map.of(
                                "delayTicks", delay,
                                "tier", tier.name(),
                                "hasLocation", loc != null,
                                "elapsedMs", elapsedMs
                        ))
                );
                // #endregion
            }, delay);
        }
    }

    public void spawnCrateDefence(CrateEntity crate) {
        if (!configManager.isCrateDefenceEnabled()) return;
        if (!configManager.isCrateDefenceTier(crate.getTier())) return;

        Location center = crate.getTargetLocation();
        if (center == null || center.getWorld() == null) return;

        World world = center.getWorld();
        CrateTier tier = crate.getTier();
        int level = tier.getLevel();

        int baseCount;
        if (level <= 5) {
            baseCount = 2 + ThreadLocalRandom.current().nextInt(2);
        } else if (level == 6) {
            baseCount = 3 + ThreadLocalRandom.current().nextInt(3);
        } else {
            baseCount = 4 + ThreadLocalRandom.current().nextInt(4);
        }

        double scale = configManager.getCrateDefenceMobScale();
        int count = Math.max(1, (int) (baseCount * scale));

        Map<String, Integer> mobPool = configManager.getGuardMobPool(tier);
        double jockeyChance = configManager.getGuardJockeyChance(tier);

        String crateIdStr = crate.getCrateId().toString();
        String guardName = ChatColor.translateAlternateColorCodes('&', "&cGuardian of the Drop");
        long startNs = System.nanoTime();

        for (int i = 0; i < count; i++) {
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double r = ThreadLocalRandom.current().nextDouble() * 10;
            double x = center.getX() + r * Math.cos(angle);
            double z = center.getZ() + r * Math.sin(angle);
            double y = world.getHighestBlockYAt((int) x, (int) z);
            Location spawnLoc = new Location(world, x, y, z);

            EntityType type = selectWeightedMob(mobPool, level);
            Entity entity = world.spawnEntity(spawnLoc, type);
            if (entity instanceof LivingEntity living) {
                tagGuard(living, guardName, crateIdStr);
                applyGuardArmor(living, tier);
                applyGuardStats(living, tier);

                if (jockeyChance > 0
                        && ThreadLocalRandom.current().nextDouble() < jockeyChance) {
                    trySpawnJockey(living, spawnLoc, crateIdStr, tier);
                }
            }
        }

        startBehaviourLoop(crate);

        Bukkit.getScheduler().runTaskLater(plugin, () -> despawnCrateDefence(crate), DEFENCE_DESPAWN_TICKS);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        // #region agent log
        DebugLogger.log(
                "review-run-1",
                "H4",
                "EventDropManager.java:spawnCrateDefence",
                "Crate defence spawned",
                new LinkedHashMap<>(Map.of(
                        "crateId", crateIdStr,
                        "tier", crate.getTier().name(),
                        "mobCount", count,
                        "elapsedMs", elapsedMs
                ))
        );
        // #endregion
    }

    public void despawnCrateDefence(CrateEntity crate) {
        if (crate == null) return;

        Integer taskId = behaviourTaskIds.remove(crate.getCrateId());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        Location center = crate.getTargetLocation();
        if (center == null || center.getWorld() == null) return;

        String crateIdStr = crate.getCrateId().toString();
        double radius = Math.max(20.0, configManager.getGuardDefendRadius() + 10);

        center.getWorld().getNearbyEntities(center, radius, radius, radius).stream()
                .filter(e -> e instanceof LivingEntity)
                .filter(e -> crateIdStr.equals(
                        e.getPersistentDataContainer().get(guardKey, PersistentDataType.STRING)))
                .forEach(e -> {
                    e.getPassengers().forEach(Entity::remove);
                    if (e.getVehicle() != null) {
                        e.getVehicle().remove();
                    }
                    e.remove();
                });
    }

    // ---- Guard spawn helpers ----

    private EntityType selectWeightedMob(Map<String, Integer> pool, int level) {
        if (pool != null && !pool.isEmpty()) {
            int total = pool.values().stream().mapToInt(Integer::intValue).sum();
            if (total > 0) {
                int roll = ThreadLocalRandom.current().nextInt(total);
                int accumulated = 0;
                for (Map.Entry<String, Integer> entry : pool.entrySet()) {
                    accumulated += entry.getValue();
                    if (roll < accumulated) {
                        try {
                            return EntityType.valueOf(entry.getKey().toUpperCase());
                        } catch (IllegalArgumentException ignored) {
                            break;
                        }
                    }
                }
            }
        }
        EntityType[] fallback;
        if (level <= 5) {
            fallback = new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON};
        } else if (level == 6) {
            fallback = new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.VINDICATOR};
        } else {
            fallback = new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON,
                    EntityType.VINDICATOR, EntityType.PILLAGER};
        }
        return fallback[ThreadLocalRandom.current().nextInt(fallback.length)];
    }

    private void tagGuard(LivingEntity entity, String guardName, String crateIdStr) {
        entity.setCustomName(guardName);
        entity.setCustomNameVisible(true);
        entity.getPersistentDataContainer().set(guardKey, PersistentDataType.STRING, crateIdStr);
    }

    private void applyGuardArmor(LivingEntity entity, CrateTier tier) {
        EntityEquipment equip = entity.getEquipment();
        if (equip == null) return;

        float dropChance = configManager.getGuardArmorDropChance(tier);
        List<String> enchants = configManager.getGuardArmorEnchantments(tier);

        ItemStack helmet = buildArmorPiece(configManager.getGuardArmorHelmet(tier), enchants);
        ItemStack chest = buildArmorPiece(configManager.getGuardArmorChestplate(tier), enchants);
        ItemStack legs = buildArmorPiece(configManager.getGuardArmorLeggings(tier), enchants);
        ItemStack boots = buildArmorPiece(configManager.getGuardArmorBoots(tier), enchants);

        if (helmet != null) {
            equip.setHelmet(helmet);
            equip.setHelmetDropChance(dropChance);
        }
        if (chest != null) {
            equip.setChestplate(chest);
            equip.setChestplateDropChance(dropChance);
        }
        if (legs != null) {
            equip.setLeggings(legs);
            equip.setLeggingsDropChance(dropChance);
        }
        if (boots != null) {
            equip.setBoots(boots);
            equip.setBootsDropChance(dropChance);
        }

        if (equip.getHelmet() == null || equip.getHelmet().getType() == Material.AIR) {
            equip.setHelmet(new ItemStack(Material.IRON_HELMET));
            equip.setHelmetDropChance(0.0f);
        }
    }

    private ItemStack buildArmorPiece(String materialName, List<String> enchants) {
        if (materialName == null || materialName.isEmpty()) return null;
        Material mat = Material.matchMaterial(materialName);
        if (mat == null) return null;
        ItemStack item = new ItemStack(mat);
        applyEnchantments(item, enchants);
        return item;
    }

    @SuppressWarnings("deprecation")
    private void applyEnchantments(ItemStack item, List<String> enchantmentStrings) {
        if (enchantmentStrings == null) return;
        for (String entry : enchantmentStrings) {
            String[] parts = entry.split(":");
            if (parts.length != 2) continue;
            Enchantment enchant = Enchantment.getByKey(
                    NamespacedKey.minecraft(parts[0].toLowerCase()));
            if (enchant == null) continue;
            try {
                int level = Integer.parseInt(parts[1]);
                item.addUnsafeEnchantment(enchant, level);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void applyGuardStats(LivingEntity entity, CrateTier tier) {
        double healthMul = configManager.getGuardHealthMultiplier(tier);
        double damageMul = configManager.getGuardDamageMultiplier(tier);
        double speedMul = configManager.getGuardSpeedMultiplier(tier);

        AttributeInstance health = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (health != null && healthMul != 1.0) {
            health.setBaseValue(health.getBaseValue() * healthMul);
            entity.setHealth(health.getValue());
        }

        AttributeInstance damage = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (damage != null && damageMul != 1.0) {
            damage.setBaseValue(damage.getBaseValue() * damageMul);
        }

        AttributeInstance speed = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speed != null && speedMul != 1.0) {
            speed.setBaseValue(speed.getBaseValue() * speedMul);
        }
    }

    private void trySpawnJockey(LivingEntity rider, Location spawnLoc,
                                String crateIdStr, CrateTier tier) {
        World world = spawnLoc.getWorld();
        if (world == null) return;

        EntityType mountType;
        if (rider instanceof Zombie zombie) {
            zombie.setBaby(true);
            mountType = ThreadLocalRandom.current().nextBoolean()
                    ? EntityType.CHICKEN : EntityType.SPIDER;
        } else if (rider.getType() == EntityType.SKELETON
                || rider.getType() == EntityType.STRAY
                || rider.getType() == EntityType.WITHER_SKELETON) {
            mountType = EntityType.SPIDER;
        } else {
            return;
        }

        Entity mount = world.spawnEntity(spawnLoc, mountType);
        if (mount instanceof LivingEntity livingMount) {
            livingMount.getPersistentDataContainer().set(
                    guardKey, PersistentDataType.STRING, crateIdStr);
            livingMount.setCustomName(
                    ChatColor.translateAlternateColorCodes('&', "&cGuardian Mount"));
            livingMount.setCustomNameVisible(false);
            applyGuardStats(livingMount, tier);
            mount.addPassenger(rider);
        }
    }

    // ---- Crate-anchor behaviour loop ----

    private void startBehaviourLoop(CrateEntity crate) {
        Location center = crate.getTargetLocation();
        if (center == null || center.getWorld() == null) return;

        String crateIdStr = crate.getCrateId().toString();
        double defendRadius = configManager.getGuardDefendRadius();
        double aggroRadius = configManager.getGuardAggroRadius();
        int interval = configManager.getGuardBehaviorTickInterval();

        double defendRadiusSq = defendRadius * defendRadius;
        double aggroRadiusSq = aggroRadius * aggroRadius;
        double scanRadius = Math.max(defendRadius, aggroRadius) + 10;

        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            World world = center.getWorld();
            if (world == null) return;

            Collection<Entity> nearby = world.getNearbyEntities(
                    center, scanRadius, scanRadius, scanRadius);

            List<Player> nearbyPlayers = new ArrayList<>();
            for (Entity e : nearby) {
                if (e instanceof Player p
                        && p.getGameMode() != GameMode.SPECTATOR
                        && p.getGameMode() != GameMode.CREATIVE
                        && p.getLocation().distanceSquared(center) <= aggroRadiusSq) {
                    nearbyPlayers.add(p);
                }
            }

            for (Entity e : nearby) {
                if (!(e instanceof Mob mob)) continue;
                if (!crateIdStr.equals(
                        mob.getPersistentDataContainer().get(
                                guardKey, PersistentDataType.STRING))) {
                    continue;
                }

                LivingEntity target = mob.getTarget();

                if (target != null
                        && target.getLocation().distanceSquared(center) > aggroRadiusSq) {
                    mob.setTarget(null);
                    target = null;
                }

                if (target == null && !nearbyPlayers.isEmpty()) {
                    Player closest = null;
                    double closestDist = Double.MAX_VALUE;
                    for (Player p : nearbyPlayers) {
                        double d = p.getLocation().distanceSquared(mob.getLocation());
                        if (d < closestDist) {
                            closestDist = d;
                            closest = p;
                        }
                    }
                    if (closest != null) {
                        mob.setTarget(closest);
                    }
                }

                if (mob.getVehicle() == null) {
                    double distSq = mob.getLocation().distanceSquared(center);
                    if (mob.getTarget() == null && distSq > defendRadiusSq) {
                        double ang = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
                        double rd = ThreadLocalRandom.current().nextDouble()
                                * (defendRadius * 0.5);
                        double nx = center.getX() + rd * Math.cos(ang);
                        double nz = center.getZ() + rd * Math.sin(ang);
                        double ny = world.getHighestBlockYAt((int) nx, (int) nz);
                        mob.teleport(new Location(world, nx, ny, nz));
                    }
                }
            }
        }, interval, interval).getTaskId();

        behaviourTaskIds.put(crate.getCrateId(), taskId);
    }

    public void checkSeasonalWindows() {
        FileConfiguration eventsConfig = configManager.getEventsConfig();
        if (eventsConfig == null) return;

        List<?> seasonalList = eventsConfig.getList("seasonal");
        if (seasonalList == null || seasonalList.isEmpty()) return;

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd"));

        for (Object raw : seasonalList) {
            if (!(raw instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) raw;
            String startStr = (String) entry.get("start");
            String endStr = (String) entry.get("end");
            String name = (String) entry.get("name");
            String poolFile = (String) entry.get("pool_file");
            String announce = (String) entry.get("announce");

            if (startStr == null || endStr == null) continue;

            if (isDateInRange(today, startStr, endStr)) {
                seasonalActive = true;
                activeEventName = name != null ? name : "Seasonal";

                var lootTableLoader = plugin.getLootTableLoader();
                if (lootTableLoader != null && poolFile != null) {
                    lootTableLoader.loadSeasonalPool(poolFile);
                }

                if (announce != null && !announce.isEmpty()) {
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', announce));
                }
                return;
            }
        }

        seasonalActive = false;
        if (activeEventName != null && !doubleDropActive) {
            activeEventName = null;
        }
    }

    private boolean isDateInRange(String date, String start, String end) {
        try {
            int dateOrd = LocalDate.parse("2000-" + date, DateTimeFormatter.ISO_LOCAL_DATE).getDayOfYear();
            int startOrd = LocalDate.parse("2000-" + start, DateTimeFormatter.ISO_LOCAL_DATE).getDayOfYear();
            int endOrd = LocalDate.parse("2000-" + end, DateTimeFormatter.ISO_LOCAL_DATE).getDayOfYear();

            if (startOrd <= endOrd) {
                return dateOrd >= startOrd && dateOrd <= endOrd;
            }
            return dateOrd >= startOrd || dateOrd <= endOrd;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isSeasonalActive() {
        return seasonalActive;
    }

    public boolean isDoubleDropActive() {
        return doubleDropActive;
    }

    public boolean isEventActive() {
        return doubleDropActive || seasonalActive;
    }

    public String getActiveEventName() {
        return activeEventName;
    }

    public void stopAll() {
        stopDoubleDrop();
        seasonalActive = false;
        activeEventName = null;
    }
}
