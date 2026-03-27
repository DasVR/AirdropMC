package dev.oblivionsanctum.airdrop;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * Loads and manages loot tables for each crate tier from YAML files.
 */
public class LootTableLoader {

    private static final String LOOT_FOLDER = "loot";
    private static final String SEASONAL_FOLDER = "loot/seasonal";

    private static final Map<CrateTier, String> TIER_FILES;
    static {
        Map<CrateTier, String> m = new java.util.LinkedHashMap<>();
        m.put(CrateTier.TIER1, "tier1_salvage.yml");
        m.put(CrateTier.TIER2, "tier2_patchwork.yml");
        m.put(CrateTier.TIER3, "tier3_ironclad.yml");
        m.put(CrateTier.TIER4, "tier4_aetheric.yml");
        m.put(CrateTier.TIER5, "tier5_brass.yml");
        m.put(CrateTier.TIER6, "tier6_sovereign.yml");
        m.put(CrateTier.TIER7, "tier7_eclipse.yml");
        TIER_FILES = Collections.unmodifiableMap(m);
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<CrateTier, LootTable> tables = new EnumMap<>(CrateTier.class);

    public LootTableLoader(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * Loads all tier loot tables and saves default resources if not present.
     */
    public void loadAll() {
        tables.clear();
        ensureLootFolderExists();

        for (CrateTier tier : CrateTier.values()) {
            String fileName = TIER_FILES.get(tier);
            if (fileName == null) continue;

            String resourcePath = LOOT_FOLDER + "/" + fileName;
            saveDefaultResource(resourcePath);

            File file = new File(plugin.getDataFolder(), resourcePath);
            LootTable table = loadLootTable(file);
            if (table != null) {
                tables.put(tier, table);
            }
        }

        saveDefaultResource(SEASONAL_FOLDER + "/halloween.yml");
    }

    private void ensureLootFolderExists() {
        File lootDir = new File(plugin.getDataFolder(), LOOT_FOLDER);
        if (!lootDir.exists()) {
            lootDir.mkdirs();
        }
        File seasonalDir = new File(plugin.getDataFolder(), SEASONAL_FOLDER);
        if (!seasonalDir.exists()) {
            seasonalDir.mkdirs();
        }
    }

    private void saveDefaultResource(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            plugin.saveResource(path, false);
        }
    }

    private LootTable loadLootTable(File file) {
        if (!file.exists()) return null;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<?> poolList = config.getList("pool");
        if (poolList == null || poolList.isEmpty()) {
            plugin.getLogger().warning("Loot table " + file.getName() + " has no pool entries");
            return null;
        }

        List<LootTable.LootEntry> pool = new ArrayList<>();
        for (Object raw : poolList) {
            if (!(raw instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) raw;
            LootTable.LootEntry entry = parseLootEntry(map);
            if (entry != null) {
                pool.add(entry);
            }
        }

        int[] itemsPerCrate = parseItemsPerCrate(config);
        boolean allowDuplicates = config.getBoolean("allow_duplicates", false);

        return new LootTable(pool, itemsPerCrate, allowDuplicates);
    }

    private LootTable.LootEntry parseLootEntry(Map<String, Object> map) {
        String materialStr = (String) map.get("material");
        if (materialStr == null || materialStr.isEmpty()) return null;

        Material material = MaterialResolver.resolveModernOnly(materialStr);
        if (material == null || material.isAir()) {
            // #region agent log
            DebugLogger.log(
                    "post-fix",
                    "H6",
                    "LootTableLoader.java:parseLootEntry",
                    "Rejected non-modern or invalid loot material",
                    new LinkedHashMap<>(Map.of("material", materialStr))
            );
            // #endregion
            plugin.getLogger().warning("Unknown or invalid material: " + materialStr);
            return null;
        }

        String name = (String) map.get("name");
        @SuppressWarnings("unchecked")
        List<String> lore = (List<String>) map.get("lore");
        @SuppressWarnings("unchecked")
        Map<String, Integer> enchantments = parseEnchantments((Map<String, Object>) map.get("enchantments"));
        int[] amountRange = parseAmountRange(map.get("amount"));
        int weight = getInt(map, "weight", 1);
        boolean mysteryOnly = getBoolean(map, "mystery_only", false);
        boolean seasonalOnly = getBoolean(map, "seasonal_only", false);

        return new LootTable.LootEntry(material, name, lore, enchantments, amountRange, weight, mysteryOnly, seasonalOnly);
    }

    private Map<String, Integer> parseEnchantments(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptyMap();
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            Object val = e.getValue();
            int level = val instanceof Number n ? n.intValue() : 1;
            result.put(e.getKey(), Math.max(1, level));
        }
        return result;
    }

    private int[] parseAmountRange(Object raw) {
        if (raw instanceof Number n) {
            int v = n.intValue();
            return new int[]{Math.max(1, v), Math.max(1, v)};
        }
        if (raw instanceof List<?> list && list.size() >= 1) {
            int a = toInt(list.get(0), 1);
            int b = list.size() >= 2 ? toInt(list.get(1), a) : a;
            return new int[]{Math.max(1, a), Math.max(1, b)};
        }
        return new int[]{1, 1};
    }

    private int[] parseItemsPerCrate(FileConfiguration config) {
        List<?> list = config.getList("items_per_crate");
        if (list == null || list.size() < 2) {
            return new int[]{3, 6};
        }
        int a = toInt(list.get(0), 3);
        int b = toInt(list.get(1), 6);
        return new int[]{Math.max(0, a), Math.max(a, b)};
    }

    private static int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object o = map.get(key);
        return o != null ? toInt(o, def) : def;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object o = map.get(key);
        if (o == null) return def;
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return Boolean.parseBoolean(s);
        return def;
    }

    /**
     * Returns the loot table for the given tier, or null if not loaded.
     */
    public LootTable getLootTable(CrateTier tier) {
        return tables.get(tier);
    }

    /**
     * Loads a seasonal pool from a YAML file (e.g. halloween.yml) and returns its LootEntry list.
     * The file is expected to be under the seasonal folder.
     */
    public List<LootTable.LootEntry> loadSeasonalPool(String poolFile) {
        String path = poolFile.contains("/") ? poolFile : SEASONAL_FOLDER + "/" + poolFile;
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            return Collections.emptyList();
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<?> poolList = config.getList("pool");
        if (poolList == null || poolList.isEmpty()) {
            return Collections.emptyList();
        }

        List<LootTable.LootEntry> entries = new ArrayList<>();
        for (Object raw : poolList) {
            if (!(raw instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) raw;
            LootTable.LootEntry entry = parseLootEntry(map);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /**
     * Clears and reloads all loot tables.
     */
    public void reload() {
        loadAll();
    }

    public Map<CrateTier, LootTable> getTables() {
        return Collections.unmodifiableMap(tables);
    }
}
