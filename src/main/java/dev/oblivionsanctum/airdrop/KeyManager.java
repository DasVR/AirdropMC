package dev.oblivionsanctum.airdrop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Manages crate keys: creation, validation, and distribution.
 */
public class KeyManager {

    private final AirdropPlugin plugin;
    private final NamespacedKey keyTag;

    public KeyManager(AirdropPlugin plugin) {
        this.plugin = plugin;
        this.keyTag = new NamespacedKey(plugin, "crate_key_tier");
    }

    /**
     * Creates a key ItemStack for the given tier.
     */
    public ItemStack createKey(CrateTier tier) {
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(tier.getColor() + tier.getDisplayName() + " Key");
        meta.setLore(List.of(
                ChatColor.translateAlternateColorCodes('&',
                        "&7Use this key to open a " + tier.getDisplayName() + " Crate")
        ));

        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, tier.name());
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Validates and consumes the player's held key for the given tier.
     * Returns true if the crate can be opened (key consumed or tier not keyed).
     */
    public boolean validateKey(Player player, CrateTier tier) {
        ConfigManager configManager = plugin.getConfigManager();
        if (!configManager.isTierKeyed(tier)) {
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cThis crate requires a " + tier.getDisplayName() + " Key."));
            return false;
        }

        PersistentDataContainer pdc = held.getItemMeta() != null
                ? held.getItemMeta().getPersistentDataContainer()
                : null;
        if (pdc == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cThis crate requires a " + tier.getDisplayName() + " Key."));
            return false;
        }

        String value = pdc.get(keyTag, PersistentDataType.STRING);
        if (value == null || !value.equals(tier.name())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cThis crate requires a " + tier.getDisplayName() + " Key."));
            return false;
        }

        held.setAmount(held.getAmount() - 1);
        return true;
    }

    /**
     * Registers shapeless crafting recipes for each tier listed in key-recipes config.
     */
    public void registerRecipes() {
        ConfigManager configManager = plugin.getConfigManager();
        if (!configManager.isKeyRecipesEnabled()) return;

        for (String tierKey : configManager.getKeyRecipeTiers()) {
            CrateTier tier = CrateTier.fromConfigKey(tierKey);
            if (tier == null) continue;

            List<String> ingredients = configManager.getKeyRecipeIngredients(tier);
            if (ingredients == null || ingredients.isEmpty()) continue;

            ItemStack result = createKey(tier);
            NamespacedKey recipeKey = new NamespacedKey(plugin, "crate_key_recipe_" + tierKey);
            ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, result);

            boolean valid = true;
            for (String matStr : ingredients) {
                Material mat = MaterialResolver.resolveModernOnly(matStr);
                if (mat != null && mat.isItem()) {
                    recipe.addIngredient(mat);
                } else {
                    plugin.getLogger().warning(
                            "Invalid material '" + matStr + "' in key recipe for " + tierKey);
                    valid = false;
                    break;
                }
            }
            if (!valid) continue;

            try {
                Bukkit.addRecipe(recipe);
            } catch (IllegalStateException e) {
                plugin.getLogger().warning(
                        "Could not register key recipe for " + tierKey + ": " + e.getMessage());
            }
        }
    }

    /**
     * Gives a key of the given tier to the player.
     */
    public void giveKey(Player player, CrateTier tier) {
        ItemStack key = createKey(tier);
        if (player.getInventory().firstEmpty() >= 0) {
            player.getInventory().addItem(key);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), key);
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&aYou received a " + tier.getDisplayName() + " Key!"));
    }
}
