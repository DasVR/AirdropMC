package dev.oblivionsanctum.airdrop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages airdrop zones: loading, querying, and picking drop locations.
 */
public class ZoneManager {

    private final AirdropPlugin plugin;
    private final Map<String, DropZone> zones;

    public ZoneManager(AirdropPlugin plugin) {
        this.plugin = plugin;
        this.zones = new LinkedHashMap<>();
    }

    /**
     * Loads zones from the zones config.
     */
    public void loadZones() {
        zones.clear();
        FileConfiguration zonesConfig = plugin.getConfigManager().getZonesConfig();
        ConfigurationSection zonesSection = zonesConfig.getConfigurationSection("zones");
        if (zonesSection == null) return;

        for (String name : zonesSection.getKeys(false)) {
            ConfigurationSection zoneSection = zonesSection.getConfigurationSection(name);
            if (zoneSection == null) continue;

            String worldName = zoneSection.getString("world", "world");
            int minX = zoneSection.getInt("min-x", 0);
            int maxX = zoneSection.getInt("max-x", 0);
            int minZ = zoneSection.getInt("min-z", 0);
            int maxZ = zoneSection.getInt("max-z", 0);
            boolean enabled = zoneSection.getBoolean("enabled", true);
            int weight = Math.max(1, zoneSection.getInt("weight", 1));
            boolean allowCave = zoneSection.getBoolean("allow-cave", true);
            double difficultyBonus = Math.max(0.0, zoneSection.getDouble("difficulty-bonus", 0.0));

            DropZone zone = new DropZone(name, worldName, minX, maxX, minZ, maxZ, enabled, weight, allowCave, difficultyBonus);
            zones.put(name, zone);
        }
    }

    /**
     * Clears and reloads zones from config.
     */
    public void reload() {
        loadZones();
    }

    /**
     * Returns all zones where enabled is true.
     */
    public List<DropZone> getEnabledZones() {
        return zones.values().stream()
                .filter(z -> z.enabled)
                .toList();
    }

    /**
     * Returns all zone names.
     */
    public List<String> getZoneNames() {
        return new ArrayList<>(zones.keySet());
    }

    /**
     * Returns the zone by name, or null.
     */
    public DropZone getZone(String name) {
        return zones.get(name);
    }

    /**
     * Returns the name of the zone containing the location, or null.
     */
    public String getZoneAt(Location location) {
        if (location == null || location.getWorld() == null) return null;
        for (Map.Entry<String, DropZone> entry : zones.entrySet()) {
            if (entry.getValue().containsLocation(location)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static final class Candidate {
        final Location location;
        final double score;
        Candidate(Location location, double score) {
            this.location = location;
            this.score = score;
        }
    }

    public Location pickDropLocation() {
        List<DropZone> enabledZones = getEnabledZones();
        if (enabledZones.isEmpty()) {
            return null;
        }

        int attempts = Math.max(4, plugin.getConfigManager().getZoneMaxAttemptsPerDrop());
        int minDistance = Math.max(0, plugin.getConfigManager().getZoneMinDistanceFromPlayers());
        int caveStepY = plugin.getConfigManager().getZoneCaveScanStepY();
        int caveMinY = plugin.getConfigManager().getZoneCaveMinY();
        boolean globalAllowCaves = plugin.getConfigManager().isZoneAllowCaveSpawns();
        double caveChance = Math.max(0.0, Math.min(1.0, plugin.getConfigManager().getZoneCaveSpawnChance()));

        // Single online-player snapshot keeps checks O(players) with no repeated lookups.
        List<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers().stream()
                .filter(Player::isOnline)
                .toList();

        // Try heatmap-picked zone first if possible.
        DropZone preferred = pickHeatmapZone(enabledZones);
        Candidate best = null;
        if (preferred != null) {
            best = searchZone(preferred, attempts / 2, minDistance, caveStepY, caveMinY, globalAllowCaves, caveChance, onlinePlayers);
            if (best != null) return best.location;
        }

        // Then search weighted random zones and keep the best candidate.
        for (int i = 0; i < attempts; i++) {
            DropZone zone = pickWeightedZone(enabledZones);
            Candidate candidate = searchZone(zone, 1, minDistance, caveStepY, caveMinY, globalAllowCaves, caveChance, onlinePlayers);
            if (candidate != null && (best == null || candidate.score > best.score)) {
                best = candidate;
            }
        }
        return best != null ? best.location : null;
    }

    /**
     * Picks a random surface location within the world border.
     * Avoids water, lava, and non-solid surfaces. Respects player distance settings.
     */
    public Location pickWorldBorderLocation() {
        ConfigManager cfg = plugin.getConfigManager();
        String worldName = cfg.getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) return null;

        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double halfSize = border.getSize() / 2.0;
        int padding = cfg.getWorldBorderPadding();

        int minX = (int) Math.ceil(center.getX() - halfSize + padding);
        int maxX = (int) Math.floor(center.getX() + halfSize - padding);
        int minZ = (int) Math.ceil(center.getZ() - halfSize + padding);
        int maxZ = (int) Math.floor(center.getZ() + halfSize - padding);

        int maxRadius = cfg.getWorldBorderMaxRadius();
        if (maxRadius > 0) {
            int cx = (int) center.getX();
            int cz = (int) center.getZ();
            minX = Math.max(minX, cx - maxRadius);
            maxX = Math.min(maxX, cx + maxRadius);
            minZ = Math.max(minZ, cz - maxRadius);
            maxZ = Math.min(maxZ, cz + maxRadius);
        }

        if (minX >= maxX || minZ >= maxZ) return null;

        int attempts = cfg.getWorldBorderMaxAttempts();
        boolean avoidWater = cfg.isWorldBorderAvoidWater();
        boolean avoidLava = cfg.isWorldBorderAvoidLava();
        int minDistFromSpawn = cfg.getWorldBorderMinDistFromSpawn();
        int minDistFromPlayers = cfg.getZoneMinDistanceFromPlayers();
        Location spawn = world.getSpawnLocation();

        boolean globalAllowCaves = cfg.isZoneAllowCaveSpawns();
        double caveChance = Math.max(0.0, Math.min(1.0, cfg.getZoneCaveSpawnChance()));
        int caveStepY = cfg.getZoneCaveScanStepY();
        int caveMinY = cfg.getZoneCaveMinY();

        List<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers().stream()
                .filter(Player::isOnline).toList();

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location bestLoc = null;
        double bestScore = -1;

        for (int i = 0; i < attempts; i++) {
            int x = rng.nextInt(minX, maxX + 1);
            int z = rng.nextInt(minZ, maxZ + 1);

            if (minDistFromSpawn > 0) {
                double dxS = x - spawn.getBlockX();
                double dzS = z - spawn.getBlockZ();
                if (dxS * dxS + dzS * dzS < (double) minDistFromSpawn * minDistFromSpawn) continue;
            }

            boolean tryCave = globalAllowCaves && rng.nextDouble() < caveChance;
            if (tryCave) {
                Location cave = findCaveLocation(world, x, z, caveStepY, caveMinY);
                if (cave != null) {
                    double dist = nearestPlayerDistance(cave, onlinePlayers);
                    if (dist >= minDistFromPlayers && (bestLoc == null || dist > bestScore)) {
                        bestLoc = cave;
                        bestScore = dist;
                    }
                    continue;
                }
            }

            int y = world.getHighestBlockYAt(x, z);
            Block surface = world.getBlockAt(x, y, z);
            Block below = world.getBlockAt(x, y - 1, z);

            if (!isSafeSurface(below.getType(), avoidWater, avoidLava)) continue;
            if (surface.getType() == Material.CACTUS || surface.getType() == Material.SWEET_BERRY_BUSH) continue;

            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            double dist = nearestPlayerDistance(loc, onlinePlayers);
            if (dist < minDistFromPlayers) continue;

            if (bestLoc == null || dist > bestScore) {
                bestLoc = loc;
                bestScore = dist;
            }

            if (dist > 500) break;
        }

        return bestLoc;
    }

    /**
     * Picks a world-border-safe location biased near a random online player.
     * Respects player-bias min/max radius and world-border padding/avoidance rules.
     *
     * Returns null if no suitable location found or no online players.
     */
    public Location pickPlayerBiasedWorldBorderLocation() {
        ConfigManager cfg = plugin.getConfigManager();
        String worldName = cfg.getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) return null;

        final World dropWorld = world;
        List<? extends Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(Player::isOnline)
                .filter(p -> p.getWorld().equals(dropWorld))
                .toList();
        if (players.isEmpty()) return null;

        WorldBorder border = dropWorld.getWorldBorder();
        Location center = border.getCenter();
        double halfSize = border.getSize() / 2.0;
        int padding = cfg.getWorldBorderPadding();

        int minX = (int) Math.ceil(center.getX() - halfSize + padding);
        int maxX = (int) Math.floor(center.getX() + halfSize - padding);
        int minZ = (int) Math.ceil(center.getZ() - halfSize + padding);
        int maxZ = (int) Math.floor(center.getZ() + halfSize - padding);

        int maxRadius = cfg.getWorldBorderMaxRadius();
        if (maxRadius > 0) {
            int cx = (int) center.getX();
            int cz = (int) center.getZ();
            minX = Math.max(minX, cx - maxRadius);
            maxX = Math.min(maxX, cx + maxRadius);
            minZ = Math.max(minZ, cz - maxRadius);
            maxZ = Math.min(maxZ, cz + maxRadius);
        }
        if (minX >= maxX || minZ >= maxZ) return null;

        boolean avoidWater = cfg.isWorldBorderAvoidWater();
        boolean avoidLava = cfg.isWorldBorderAvoidLava();
        int minDistFromSpawn = cfg.getWorldBorderMinDistFromSpawn();
        Location spawn = dropWorld.getSpawnLocation();

        int minR = cfg.getPlayerBiasMinRadius();
        int maxR = cfg.getPlayerBiasMaxRadius();
        int attempts = cfg.getPlayerBiasMaxAttempts();

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int i = 0; i < attempts; i++) {
            Player anchor = players.get(rng.nextInt(players.size()));
            Location base = anchor.getLocation();

            double angle = rng.nextDouble(0, Math.PI * 2);
            double radius = (minR >= maxR) ? minR : rng.nextDouble(minR, maxR);
            int x = (int) Math.round(base.getX() + Math.cos(angle) * radius);
            int z = (int) Math.round(base.getZ() + Math.sin(angle) * radius);

            if (x < minX || x > maxX || z < minZ || z > maxZ) continue;

            if (minDistFromSpawn > 0) {
                double dxS = x - spawn.getBlockX();
                double dzS = z - spawn.getBlockZ();
                if (dxS * dxS + dzS * dzS < (double) minDistFromSpawn * minDistFromSpawn) continue;
            }

            int y = dropWorld.getHighestBlockYAt(x, z);
            Block surface = dropWorld.getBlockAt(x, y, z);
            Block below = dropWorld.getBlockAt(x, y - 1, z);

            if (!isSafeSurface(below.getType(), avoidWater, avoidLava)) continue;
            if (surface.getType() == Material.CACTUS || surface.getType() == Material.SWEET_BERRY_BUSH) continue;

            return new Location(dropWorld, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    private static boolean isSafeSurface(Material mat, boolean avoidWater, boolean avoidLava) {
        if (!mat.isSolid()) return false;
        if (avoidWater && mat == Material.WATER) return false;
        if (avoidLava && mat == Material.LAVA) return false;
        return true;
    }

    private static Location findCaveLocation(World world, int x, int z, int caveStepY, int caveMinY) {
        int maxY = world.getHighestBlockYAt(x, z);
        int minY = Math.max(world.getMinHeight() + 4, caveMinY);
        for (int y = maxY - 2; y >= minY; y -= caveStepY) {
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            Block floor = world.getBlockAt(x, y - 1, z);
            if (!feet.getType().isAir() || !head.getType().isAir()) continue;
            if (!floor.getType().isSolid()) continue;
            if (floor.getType() == Material.LAVA || floor.getType() == Material.WATER) continue;
            return new Location(world, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    private DropZone pickHeatmapZone(List<DropZone> enabledZones) {
        var heatmapTracker = plugin.getHeatmapTracker();
        if (heatmapTracker == null) return null;
        List<String> enabledNames = enabledZones.stream().map(z -> z.name).toList();
        String pickedName = heatmapTracker.pickBiasedZone(enabledNames);
        return zones.get(pickedName);
    }

    private DropZone pickWeightedZone(List<DropZone> enabledZones) {
        int total = 0;
        for (DropZone z : enabledZones) total += Math.max(1, z.weight);
        if (total <= 0) return enabledZones.get(ThreadLocalRandom.current().nextInt(enabledZones.size()));

        int roll = ThreadLocalRandom.current().nextInt(total);
        int acc = 0;
        for (DropZone z : enabledZones) {
            acc += Math.max(1, z.weight);
            if (roll < acc) return z;
        }
        return enabledZones.get(enabledZones.size() - 1);
    }

    private Candidate searchZone(
            DropZone zone,
            int attempts,
            int minDistanceFromPlayers,
            int caveStepY,
            int caveMinY,
            boolean globalAllowCaves,
            double caveChance,
            List<? extends Player> onlinePlayers
    ) {
        Candidate best = null;
        for (int i = 0; i < attempts; i++) {
            Location candidate = zone.getRandomLocation(globalAllowCaves, caveChance, caveStepY, caveMinY);
            if (candidate == null || candidate.getWorld() == null) continue;

            double nearest = nearestPlayerDistance(candidate, onlinePlayers);
            if (nearest < minDistanceFromPlayers) continue;

            double score = nearest + zone.difficultyBonus * 100.0;
            Candidate c = new Candidate(candidate, score);
            if (best == null || c.score > best.score) {
                best = c;
            }
        }
        return best;
    }

    private double nearestPlayerDistance(Location loc, List<? extends Player> onlinePlayers) {
        World world = loc.getWorld();
        if (world == null || onlinePlayers.isEmpty()) return Double.MAX_VALUE;

        return onlinePlayers.stream()
                .filter(p -> p.getWorld().equals(world))
                .mapToDouble(p -> Math.sqrt(p.getLocation().distanceSquared(loc)))
                .min()
                .orElse(Double.MAX_VALUE);
    }

    /**
     * Adds a new zone from two corner locations and persists to zones.yml.
     */
    public void addZone(String name, Location corner1, Location corner2) {
        if (corner1 == null || corner2 == null || corner1.getWorld() == null || corner2.getWorld() == null) {
            return;
        }
        String worldName = corner1.getWorld().getName();
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        DropZone zone = new DropZone(name, worldName, minX, maxX, minZ, maxZ, true, 1, true, 0.0);
        zones.put(name, zone);

        FileConfiguration zonesConfig = plugin.getConfigManager().getZonesConfig();
        String path = "zones." + name;
        zonesConfig.set(path + ".world", worldName);
        zonesConfig.set(path + ".min-x", minX);
        zonesConfig.set(path + ".max-x", maxX);
        zonesConfig.set(path + ".min-z", minZ);
        zonesConfig.set(path + ".max-z", maxZ);
        zonesConfig.set(path + ".enabled", true);
        zonesConfig.set(path + ".weight", 1);
        zonesConfig.set(path + ".allow-cave", true);
        zonesConfig.set(path + ".difficulty-bonus", 0.0);

        saveZonesConfig();
    }

    /**
     * Removes a zone by name and persists to zones.yml.
     */
    public void removeZone(String name) {
        zones.remove(name);
        plugin.getConfigManager().getZonesConfig().set("zones." + name, null);
        saveZonesConfig();
    }

    private void saveZonesConfig() {
        try {
            File file = new File(plugin.getDataFolder(), "zones.yml");
            plugin.getConfigManager().getZonesConfig().save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save zones.yml: " + e.getMessage());
        }
    }

    /**
     * Represents a rectangular drop zone in a world.
     */
    public static class DropZone {
        private final String name;
        private final String worldName;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final boolean enabled;
        private final int weight;
        private final boolean allowCave;
        private final double difficultyBonus;

        public DropZone(String name, String worldName, int minX, int maxX, int minZ, int maxZ,
                        boolean enabled, int weight, boolean allowCave, double difficultyBonus) {
            this.name = name;
            this.worldName = worldName;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.enabled = enabled;
            this.weight = Math.max(1, weight);
            this.allowCave = allowCave;
            this.difficultyBonus = Math.max(0.0, difficultyBonus);
        }

        /**
         * Returns true if the location is within this zone (same world and bounds).
         */
        public boolean containsLocation(Location loc) {
            if (loc == null || loc.getWorld() == null) return false;
            if (!loc.getWorld().getName().equals(worldName)) return false;
            int x = loc.getBlockX();
            int z = loc.getBlockZ();
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        public Location getRandomLocation(boolean globalAllowCaves, double caveChance, int caveStepY, int caveMinY) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;

            ThreadLocalRandom random = ThreadLocalRandom.current();
            int x = minX == maxX ? minX : random.nextInt(minX, maxX + 1);
            int z = minZ == maxZ ? minZ : random.nextInt(minZ, maxZ + 1);

            boolean tryCave = globalAllowCaves && allowCave && random.nextDouble() < caveChance;
            if (tryCave) {
                Location cave = findCaveLocation(world, x, z, caveStepY, caveMinY);
                if (cave != null) return cave;
            }

            int y = world.getHighestBlockYAt(x, z);
            return new Location(world, x + 0.5, y, z + 0.5);
        }

        private Location findCaveLocation(World world, int x, int z, int caveStepY, int caveMinY) {
            int maxY = world.getHighestBlockYAt(x, z);
            int minY = Math.max(world.getMinHeight() + 4, caveMinY);

            for (int y = maxY - 2; y >= minY; y -= caveStepY) {
                Block feet = world.getBlockAt(x, y, z);
                Block head = world.getBlockAt(x, y + 1, z);
                Block floor = world.getBlockAt(x, y - 1, z);

                if (!feet.getType().isAir() || !head.getType().isAir()) continue;
                if (!floor.getType().isSolid()) continue;
                if (floor.getType() == Material.LAVA || floor.getType() == Material.WATER) continue;

                return new Location(world, x + 0.5, y, z + 0.5);
            }
            return null;
        }

        public String getName() { return name; }
        public String getWorldName() { return worldName; }
        public int getMinX() { return minX; }
        public int getMaxX() { return maxX; }
        public int getMinZ() { return minZ; }
        public int getMaxZ() { return maxZ; }
        public boolean isEnabled() { return enabled; }
        public int getWeight() { return weight; }
        public boolean isAllowCave() { return allowCave; }
        public double getDifficultyBonus() { return difficultyBonus; }
    }
}
