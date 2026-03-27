package dev.oblivionsanctum.airdrop;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves crate claims: direct give or squad-split distribution into shulker boxes.
 */
public class ClaimManager {

    private final AirdropPlugin plugin;

    public ClaimManager(AirdropPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Resolves the claim for the given crate: gives loot directly or splits among nearby players.
     */
    public void resolveClaim(CrateEntity crate, Player opener, List<ItemStack> loot) {
        ConfigManager config = plugin.getConfigManager();

        if (!config.isSquadSplit() || !config.isSquadSplitTier(crate.getTier())) {
            giveItems(opener, loot);
            return;
        }

        Location crateLoc = crate.getTargetLocation();
        if (crateLoc == null || crateLoc.getWorld() == null) {
            giveItems(opener, loot);
            return;
        }

        int radius = config.getProximityRadius();
        List<Player> nearby = crateLoc.getWorld().getPlayers().stream()
                .filter(p -> p.isOnline() && p.getLocation().distance(crateLoc) <= radius)
                .toList();

        if (nearby.size() <= 1) {
            giveItems(opener, loot);
            return;
        }

        List<List<ItemStack>> splits = splitLoot(loot, nearby.size(), opener, nearby);
        String tierName = crate.getTier().getDisplayName();
        String shulkerName = ChatColor.translateAlternateColorCodes('&',
                "&6[" + tierName + " Crate - Your Share]");

        for (int i = 0; i < nearby.size(); i++) {
            Player recipient = nearby.get(i);
            List<ItemStack> share = splits.get(i);
            if (share.isEmpty()) continue;

            ItemStack shulker = new ItemStack(Material.ORANGE_SHULKER_BOX);
            BlockStateMeta meta = (BlockStateMeta) shulker.getItemMeta();
            if (meta == null) continue;

            meta.setDisplayName(shulkerName);
            ShulkerBox box = (ShulkerBox) meta.getBlockState();
            for (int slot = 0; slot < Math.min(share.size(), 27); slot++) {
                box.getInventory().setItem(slot, share.get(slot));
            }
            meta.setBlockState(box);
            shulker.setItemMeta(meta);

            giveItems(recipient, List.of(shulker));
        }

        if (config.isLogDrops()) {
            logRecipients(crate, nearby);
        }
    }

    private List<List<ItemStack>> splitLoot(List<ItemStack> loot, int playerCount,
                                            Player opener, List<Player> players) {
        List<List<ItemStack>> splits = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            splits.add(new ArrayList<>());
        }

        int openerIdx = players.indexOf(opener);
        if (openerIdx < 0) openerIdx = 0;

        int baseSize = loot.size() / playerCount;
        int remainder = loot.size() % playerCount;

        int itemIdx = 0;
        for (int p = 0; p < playerCount; p++) {
            int count = baseSize + (p == openerIdx ? remainder : 0);
            for (int j = 0; j < count && itemIdx < loot.size(); j++) {
                splits.get(p).add(loot.get(itemIdx++));
            }
        }

        return splits;
    }

    /**
     * Gives items to the player; drops excess at their feet if inventory is full.
     */
    public void giveItems(Player player, List<ItemStack> items) {
        if (player == null || items == null || items.isEmpty()) return;

        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;

            Map<Integer, ItemStack> excess = player.getInventory().addItem(item);
            for (ItemStack drop : excess.values()) {
                if (drop != null && !drop.getType().isAir()) {
                    player.getWorld().dropItem(player.getLocation(), drop);
                }
            }
        }
    }

    private void logRecipients(CrateEntity crate, List<Player> recipients) {
        String logFile = plugin.getConfigManager().getLogFile();
        File file = new File(plugin.getDataFolder(), logFile);

        StringBuilder line = new StringBuilder();
        line.append(String.format("[Squad Split] %s crate at %d,%d,%d - recipients: ",
                crate.getTier().getDisplayName(),
                crate.getTargetLocation().getBlockX(),
                crate.getTargetLocation().getBlockY(),
                crate.getTargetLocation().getBlockZ()));
        for (int i = 0; i < recipients.size(); i++) {
            if (i > 0) line.append(", ");
            line.append(recipients.get(i).getName());
        }
        line.append("\n");

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(line.toString());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write squad split log: " + e.getMessage());
        }
    }
}
