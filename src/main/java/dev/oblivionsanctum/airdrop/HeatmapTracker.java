package dev.oblivionsanctum.airdrop;

import org.bukkit.Location;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tracks zone drop times for biased zone selection (heatmap).
 */
public class HeatmapTracker {

    private final AirdropPlugin plugin;
    private final Map<String, Long> zoneLastDropTime;
    private File dataFile;

    public HeatmapTracker(AirdropPlugin plugin) {
        this.plugin = plugin;
        this.zoneLastDropTime = new ConcurrentHashMap<>();
    }

    /**
     * Initializes the heatmap tracker: loads persisted data if heatmap is enabled.
     */
    public void init() {
        var configManager = plugin.getConfigManager();
        if (!configManager.isHeatmapEnabled()) {
            return;
        }

        this.dataFile = new File(plugin.getDataFolder(), configManager.getHeatmapFile());
        load();
    }

    /**
     * Records a drop for the given zone name.
     *
     * @param zoneName the zone identifier
     */
    public void recordDrop(String zoneName) {
        if (zoneName != null && !zoneName.isBlank()) {
            zoneLastDropTime.put(zoneName, System.currentTimeMillis());
        }
    }

    /**
     * Records a drop at the given location by resolving the zone.
     *
     * @param location the drop location
     */
    public void recordDrop(Location location) {
        if (location == null) {
            return;
        }

        var zoneManager = plugin.getZoneManager();
        if (zoneManager == null) {
            return;
        }

        String zoneName = zoneManager.getZoneAt(location);
        if (zoneName != null) {
            recordDrop(zoneName);
        }
    }

    /**
     * Picks a zone from the list, biased toward zones that have not received drops recently.
     *
     * @param zoneNames list of zone names to choose from
     * @return the selected zone name, or a random one if all are new
     */
    public String pickBiasedZone(List<String> zoneNames) {
        if (zoneNames == null || zoneNames.isEmpty()) {
            return null;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        long now = System.currentTimeMillis();

        boolean allNew = true;
        String bestZone = null;
        double bestScore = -1;

        for (String zone : zoneNames) {
            long lastDrop = zoneLastDropTime.getOrDefault(zone, 0L);
            if (lastDrop > 0) {
                allNew = false;
            }

            long age = now - lastDrop;
            double factor = 0.8 + random.nextDouble() * 0.4;
            double score = age * factor;

            if (score > bestScore) {
                bestScore = score;
                bestZone = zone;
            }
        }

        if (allNew) {
            return zoneNames.get(random.nextInt(zoneNames.size()));
        }

        return bestZone;
    }

    /**
     * Saves zone drop times to the data file.
     */
    public void save() {
        if (dataFile == null) {
            return;
        }

        try {
            plugin.getDataFolder().mkdirs();
            Properties props = new Properties();
            for (Map.Entry<String, Long> entry : zoneLastDropTime.entrySet()) {
                props.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
            }
            try (OutputStream out = new FileOutputStream(dataFile)) {
                props.store(out, "Oblivion Sanctum Airdrop Heatmap");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save heatmap data: " + e.getMessage());
        }
    }

    /**
     * Loads zone drop times from the data file.
     */
    public void load() {
        if (dataFile == null || !dataFile.exists()) {
            return;
        }

        try (InputStream in = new FileInputStream(dataFile)) {
            Properties props = new Properties();
            props.load(in);
            zoneLastDropTime.clear();
            for (String key : props.stringPropertyNames()) {
                try {
                    long timestamp = Long.parseLong(props.getProperty(key));
                    zoneLastDropTime.put(key, timestamp);
                } catch (NumberFormatException ignored) {
                    // Skip invalid entries
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load heatmap data: " + e.getMessage());
        }
    }
}
