package dev.oblivionsanctum.airdrop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks the race to airdrops with coordinate-based boss bars
 * colored by crate tier, and awards salvager bonuses.
 */
public class RaceTracker {

    private final AirdropPlugin plugin;
    private final Map<UUID, Integer> trackingTasks;
    private final Map<UUID, BossBar> bossBars;
    private final Map<UUID, Boolean> incomingTimeoutAnnounced;

    public RaceTracker(AirdropPlugin plugin) {
        this.plugin = plugin;
        this.trackingTasks = new HashMap<>();
        this.bossBars = new HashMap<>();
        this.incomingTimeoutAnnounced = new HashMap<>();
    }

    /**
     * Starts tracking the race to the given crate with a coordinate-based boss bar.
     */
    public void startTracking(CrateEntity crate) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isRaceTracker()) return;

        UUID crateId = crate.getCrateId();
        if (trackingTasks.containsKey(crateId)) return;

        Location target = crate.getTargetLocation();
        CrateTier tier = crate.getTier();
        BarColor barColor = crate.isMystery() ? BarColor.PINK : tier.getBossBarColor();

        String tierDisplay = crate.isMystery() ? "Mystery" : tier.getDisplayName();
        String title = formatTitle(cfg.getBossbarTitleIncoming(), target, tierDisplay, null, cfg.isBossbarShowWorld());

        BossBar bossBar = Bukkit.createBossBar(
                ChatColor.translateAlternateColorCodes('&', title),
                barColor,
                BarStyle.SOLID
        );

        int interval = cfg.getBossbarUpdateIntervalTicks();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Location loc = crate.getTargetLocation();
            if (loc == null || loc.getWorld() == null) return;

            String td = crate.isMystery() ? "Mystery" : crate.getTier().getDisplayName();

            if (!crate.isLanded() && cfg.isBossbarIncomingTimeoutEnabled()) {
                long timeoutMs = cfg.getBossbarIncomingTimeoutMinutes() * 60_000L;
                if (crate.isExpired(timeoutMs)) {
                    if (!incomingTimeoutAnnounced.getOrDefault(crateId, false)) {
                        incomingTimeoutAnnounced.put(crateId, true);
                        String msg = cfg.getBossbarIncomingTimeoutMessage()
                                .replace("{x}", String.valueOf(loc.getBlockX()))
                                .replace("{z}", String.valueOf(loc.getBlockZ()))
                                .replace("{tier}", td);
                        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));
                    }
                    stopTracking(crate);
                    return;
                }
            }

            if (crate.isLanded()) {
                bossBar.setTitle(ChatColor.translateAlternateColorCodes('&',
                        formatTitle(cfg.getBossbarTitleLanded(), loc, td, null, cfg.isBossbarShowWorld())));
            } else {
                bossBar.setTitle(ChatColor.translateAlternateColorCodes('&',
                        formatTitle(cfg.getBossbarTitleIncoming(), loc, td, null, cfg.isBossbarShowWorld())));
            }

            bossBar.removeAll();
            for (Player p : loc.getWorld().getPlayers()) {
                if (p.isOnline()) {
                    bossBar.addPlayer(p);
                }
            }
        }, 0, interval);

        trackingTasks.put(crateId, task.getTaskId());
        bossBars.put(crateId, bossBar);
    }

    /**
     * Transitions the boss bar to "claimed" state, keeping tier color, then removes after linger period.
     */
    public void markClaimed(CrateEntity crate, Player opener) {
        UUID crateId = crate.getCrateId();
        BossBar bossBar = bossBars.get(crateId);
        if (bossBar == null) {
            stopTracking(crate);
            return;
        }

        Integer taskId = trackingTasks.remove(crateId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        incomingTimeoutAnnounced.remove(crateId);

        ConfigManager cfg = plugin.getConfigManager();
        Location loc = crate.getTargetLocation();
        String claimedTitle = formatTitle(cfg.getBossbarTitleClaimed(), loc,
                crate.isMystery() ? "Mystery" : crate.getTier().getDisplayName(),
                opener.getName(), cfg.isBossbarShowWorld());
        bossBar.setTitle(ChatColor.translateAlternateColorCodes('&', claimedTitle));

        int lingerTicks = cfg.getBossbarClaimedLingerTicks();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            bossBar.removeAll();
            bossBars.remove(crateId);
        }, lingerTicks);
    }

    /**
     * Stops tracking the given crate immediately.
     */
    public void stopTracking(CrateEntity crate) {
        UUID crateId = crate.getCrateId();
        Integer taskId = trackingTasks.remove(crateId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        BossBar bossBar = bossBars.remove(crateId);
        if (bossBar != null) {
            bossBar.removeAll();
        }
        incomingTimeoutAnnounced.remove(crateId);
    }

    /**
     * Awards the salvager bonus using config-driven XP values.
     */
    public void awardSalvagerBonus(CrateEntity crate, Player opener) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isSalvagerBonus() || opener == null) return;

        CrateTier tier = crate.getTier();
        int xp = cfg.getSalvagerXp(tier);

        opener.giveExp(xp);

        if (tier == CrateTier.TIER1) {
            giveRandomCommonItem(opener);
        }

        if (tier.getLevel() >= 3) {
            String titleMsg = tier.getColor() + tier.getDisplayName() + " Opener";
            opener.sendTitle(
                    ChatColor.translateAlternateColorCodes('&', titleMsg),
                    "", 10, 70, 20
            );
        }

        String format = cfg.getSalvagerFormat()
                .replace("{player}", opener.getName())
                .replace("{xp}", String.valueOf(xp));
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', format));
    }

    private void giveRandomCommonItem(Player player) {
        var lootTableLoader = plugin.getLootTableLoader();
        if (lootTableLoader == null) return;

        LootTable table = lootTableLoader.getLootTable(CrateTier.TIER1);
        if (table == null) return;

        List<ItemStack> items = table.generate(false, false);
        if (!items.isEmpty()) {
            ItemStack item = items.get(0);
            if (item != null && !item.getType().isAir()) {
                Map<Integer, ItemStack> excess = player.getInventory().addItem(item);
                for (ItemStack drop : excess.values()) {
                    player.getWorld().dropItem(player.getLocation(), drop);
                }
            }
        }
    }

    /**
     * Cancels all tracking tasks and removes all boss bars.
     */
    public void stopAll() {
        for (Integer taskId : trackingTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        trackingTasks.clear();
        incomingTimeoutAnnounced.clear();

        for (BossBar bossBar : bossBars.values()) {
            bossBar.removeAll();
        }
        bossBars.clear();
    }

    // ── Formatting utility ──────────────────────────────────────────────

    private static String formatTitle(String template, Location loc, String tierDisplay, String playerName, boolean showWorld) {
        if (template == null) return "";
        String result = template
                .replace("{x}", loc != null ? String.valueOf(loc.getBlockX()) : "?")
                .replace("{z}", loc != null ? String.valueOf(loc.getBlockZ()) : "?")
                .replace("{tier}", tierDisplay != null ? tierDisplay : "");
        if (playerName != null) {
            result = result.replace("{player}", playerName);
        }
        if (showWorld && loc != null && loc.getWorld() != null) {
            result = result.replace("{world}", loc.getWorld().getName());
        } else {
            result = result.replace("{world}", "");
        }
        return result;
    }
}
