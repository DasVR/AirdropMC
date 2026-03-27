package dev.oblivionsanctum.airdrop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages drop flares: custom recipe, cooldowns, and right-click to spawn airdrops.
 */
public class FlareManager implements Listener {

    private static final String FLARE_TAG = "true";

    private final AirdropPlugin plugin;
    private final ConfigManager configManager;
    private final Map<UUID, Long> cooldowns;
    private final NamespacedKey flareKey;

    public FlareManager(AirdropPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.cooldowns = new HashMap<>();
        this.flareKey = new NamespacedKey(plugin, "airdrop_flare");
    }

    public void registerRecipe() {
        if (!configManager.isFlaresEnabled()) return;

        ItemStack result = createFlareItem();
        NamespacedKey key = new NamespacedKey(plugin, "drop_flare_recipe");

        ShapelessRecipe recipe = new ShapelessRecipe(key, result);

        List<String> materials = configManager.getFlareRecipe();
        if (materials == null || materials.isEmpty()) {
            recipe.addIngredient(Material.FIREWORK_STAR);
            recipe.addIngredient(Material.BLAZE_POWDER);
            recipe.addIngredient(Material.GOLD_INGOT);
        } else {
            for (String matStr : materials) {
                Material mat = MaterialResolver.resolveModernOnly(matStr);
                if (mat != null && mat.isItem()) {
                    recipe.addIngredient(mat);
                } else {
                    // #region agent log
                    DebugLogger.log(
                            "post-fix",
                            "H6",
                            "FlareManager.java:registerRecipe",
                            "Rejected non-modern or invalid flare recipe material",
                            new HashMap<>(Map.of("material", String.valueOf(matStr)))
                    );
                    // #endregion
                }
            }
        }

        try {
            Bukkit.addRecipe(recipe);
        } catch (IllegalStateException e) {
            plugin.getLogger().warning("Could not register flare recipe: " + e.getMessage());
        }
    }

    public ItemStack createFlareItem() {
        ItemStack item = new ItemStack(Material.FIREWORK_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6Drop Flare"));
            meta.setLore(List.of(ChatColor.translateAlternateColorCodes('&', "&7Right-click to call a drop")));
            meta.getPersistentDataContainer().set(flareKey, PersistentDataType.STRING, FLARE_TAG);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack held = event.getItem();
        if (held == null || held.getType() == Material.AIR) return;
        if (!isFlare(held)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        long cooldownMs = configManager.getFlareCooldown() * 1000L;
        long lastUse = cooldowns.getOrDefault(uuid, 0L);
        long elapsed = System.currentTimeMillis() - lastUse;
        if (elapsed < cooldownMs) {
            long remaining = (cooldownMs - elapsed) / 1000;
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cYour Drop Flare is still on cooldown. " + remaining + " seconds remaining."));
            return;
        }

        held.setAmount(held.getAmount() - 1);

        String tierKey = configManager.getFlareResultTier();
        CrateTier tier = CrateTier.fromConfigKey(tierKey != null ? tierKey : "tier2");
        if (tier == null) {
            tier = CrateTier.TIER2;
        }

        var airdropManager = plugin.getAirdropManager();
        if (airdropManager != null) {
            airdropManager.spawnAt(tier, player.getLocation(), false, AirdropEvent.TriggerSource.FLARE);
        }

        cooldowns.put(uuid, System.currentTimeMillis());

        if (configManager.isFlareAnnounce()) {
            String msg = ChatColor.translateAlternateColorCodes('&',
                    "&6" + player.getName() + " launched a Drop Flare!");
            Bukkit.broadcastMessage(msg);
        }
    }

    private boolean isFlare(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return FLARE_TAG.equals(meta.getPersistentDataContainer().get(flareKey, PersistentDataType.STRING));
    }
}
