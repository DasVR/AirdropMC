package dev.oblivionsanctum.airdrop;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents one tier's loot pool loaded from YAML.
 * Supports weighted random selection with optional mystery-only and seasonal-only entries.
 */
public class LootTable {

    private final List<LootEntry> pool;
    private final int[] itemsPerCrate;
    private final boolean allowDuplicates;

    public LootTable(List<LootEntry> pool, int[] itemsPerCrate, boolean allowDuplicates) {
        this.pool = new ArrayList<>(pool);
        this.itemsPerCrate = itemsPerCrate != null && itemsPerCrate.length >= 2
                ? new int[]{itemsPerCrate[0], itemsPerCrate[1]}
                : new int[]{1, 1};
        this.allowDuplicates = allowDuplicates;
    }

    /**
     * Generates a list of ItemStacks from the loot pool based on the given flags.
     *
     * @param isMystery      whether this is a mystery crate (includes mystery_only entries)
     * @param seasonalActive whether seasonal entries should be included
     * @return list of generated ItemStacks
     */
    public List<ItemStack> generate(boolean isMystery, boolean seasonalActive) {
        List<LootEntry> eligible = new ArrayList<>();
        for (LootEntry entry : pool) {
            if (entry.mysteryOnly && !isMystery) continue;
            if (entry.seasonalOnly && !seasonalActive) continue;
            eligible.add(entry);
        }

        if (eligible.isEmpty()) {
            return Collections.emptyList();
        }

        int minItems = Math.max(0, itemsPerCrate[0]);
        int maxItems = Math.max(minItems, itemsPerCrate[1]);
        int itemCount = minItems == maxItems ? minItems
                : ThreadLocalRandom.current().nextInt(minItems, maxItems + 1);

        List<ItemStack> result = new ArrayList<>(itemCount);
        Set<Integer> usedIndices = allowDuplicates ? null : new HashSet<>();

        for (int i = 0; i < itemCount; i++) {
            int[] selected = selectWeighted(eligible, usedIndices);
            if (selected == null) break;
            int idx = selected[0];
            LootEntry entry = eligible.get(idx);

            ItemStack item = createItemStack(entry);
            if (item != null && !item.getType().isAir()) {
                result.add(item);
            }

            if (!allowDuplicates) {
                usedIndices.add(idx);
            }
        }

        return result;
    }

    private int[] selectWeighted(List<LootEntry> eligible, Set<Integer> usedIndices) {
        int totalWeight = 0;
        List<Integer> availableIndices = new ArrayList<>();
        for (int i = 0; i < eligible.size(); i++) {
            if (usedIndices != null && usedIndices.contains(i)) continue;
            totalWeight += eligible.get(i).weight;
            availableIndices.add(i);
        }

        if (totalWeight <= 0 || availableIndices.isEmpty()) return null;

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int accumulated = 0;
        for (int idx : availableIndices) {
            accumulated += eligible.get(idx).weight;
            if (roll < accumulated) return new int[]{idx};
        }
        return new int[]{availableIndices.get(availableIndices.size() - 1)};
    }

    private ItemStack createItemStack(LootEntry entry) {
        Material material = entry.material;
        if (material == null || material.isAir()) return null;

        int min = entry.amountRange[0];
        int max = entry.amountRange.length > 1 ? entry.amountRange[1] : min;
        int amount = min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        amount = Math.max(1, Math.min(amount, material.getMaxStackSize()));

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (entry.name != null && !entry.name.isEmpty()) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', entry.name));
        }
        if (entry.lore != null && !entry.lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : entry.lore) {
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(coloredLore);
        }
        if (entry.enchantments != null && !entry.enchantments.isEmpty()) {
            for (Map.Entry<String, Integer> enc : entry.enchantments.entrySet()) {
                Enchantment enchantment = resolveEnchantment(enc.getKey());
                if (enchantment != null) {
                    if (material == Material.ENCHANTED_BOOK && meta instanceof EnchantmentStorageMeta esm) {
                        esm.addStoredEnchant(enchantment, enc.getValue(), true);
                    } else {
                        meta.addEnchant(enchantment, enc.getValue(), true);
                    }
                }
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    private static Enchantment resolveEnchantment(String key) {
        if (key == null || key.isEmpty()) return null;
        String normalized = key.toLowerCase(Locale.ROOT).replace(' ', '_');
        NamespacedKey nk = NamespacedKey.minecraft(normalized);
        return Enchantment.getByKey(nk);
    }

    /**
     * Returns a new LootTable with the given seasonal entries merged into the pool.
     * The original itemsPerCrate and allowDuplicates settings are preserved.
     */
    public LootTable mergePool(List<LootEntry> seasonalEntries) {
        List<LootEntry> merged = new ArrayList<>(pool);
        if (seasonalEntries != null) {
            merged.addAll(seasonalEntries);
        }
        return new LootTable(merged, itemsPerCrate, allowDuplicates);
    }

    public List<LootEntry> getPool() {
        return Collections.unmodifiableList(pool);
    }

    public int[] getItemsPerCrate() {
        return itemsPerCrate.clone();
    }

    public boolean isAllowDuplicates() {
        return allowDuplicates;
    }

    /**
     * A single loot entry in the pool.
     */
    public static final class LootEntry {
        public final Material material;
        public final String name;
        public final List<String> lore;
        public final Map<String, Integer> enchantments;
        public final int[] amountRange;
        public final int weight;
        public final boolean mysteryOnly;
        public final boolean seasonalOnly;

        public LootEntry(Material material, String name, List<String> lore,
                        Map<String, Integer> enchantments, int[] amountRange,
                        int weight, boolean mysteryOnly, boolean seasonalOnly) {
            this.material = material;
            this.name = name;
            this.lore = lore != null ? List.copyOf(lore) : Collections.emptyList();
            this.enchantments = enchantments != null ? Map.copyOf(enchantments) : Collections.emptyMap();
            this.amountRange = amountRange != null && amountRange.length >= 1
                    ? amountRange.clone() : new int[]{1, 1};
            this.weight = Math.max(1, weight);
            this.mysteryOnly = mysteryOnly;
            this.seasonalOnly = seasonalOnly;
        }
    }
}
