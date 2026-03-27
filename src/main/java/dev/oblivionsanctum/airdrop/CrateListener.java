package dev.oblivionsanctum.airdrop;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class CrateListener implements Listener {

    private final AirdropPlugin plugin;
    private final NamespacedKey crateIdKey;

    public CrateListener(AirdropPlugin plugin) {
        this.plugin = plugin;
        this.crateIdKey = new NamespacedKey(plugin, "airdrop_id");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof FallingBlock)) return;

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String crateIdStr = pdc.get(crateIdKey, PersistentDataType.STRING);
        if (crateIdStr == null) return;

        event.setCancelled(true);

        UUID crateId = UUID.fromString(crateIdStr);
        CrateEntity crate = plugin.getAirdropManager().getActiveCrates().get(crateId);
        if (crate == null) {
            entity.remove();
            return;
        }

        entity.remove();
        plugin.getAirdropManager().handleCrateLand(crate);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        Material type = block.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST && type != Material.ENDER_CHEST) return;

        CrateEntity matchedCrate = null;
        for (CrateEntity crate : plugin.getAirdropManager().getActiveCrates().values()) {
            if (!crate.isLanded()) continue;
            if (crate.getTargetLocation().getBlockX() == block.getX()
                    && crate.getTargetLocation().getBlockY() == block.getY()
                    && crate.getTargetLocation().getBlockZ() == block.getZ()
                    && crate.getTargetLocation().getWorld().equals(block.getWorld())) {
                matchedCrate = crate;
                break;
            }
        }

        if (matchedCrate == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        plugin.getAirdropManager().handleCrateOpen(matchedCrate, player);
    }
}
