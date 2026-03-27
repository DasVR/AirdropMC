package dev.oblivionsanctum.airdrop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Main command handler for the airdrop plugin.
 */
public class AirdropCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "drop", "event", "reload", "zones", "key", "top", "history",
            "status", "compass", "replayfx", "delete"
    );

    private static final List<String> EVENT_SUBS = List.of("double", "eclipse", "surge");
    private static final List<String> ZONES_SUBS = List.of("list", "add", "remove");
    private static final List<String> KEY_SUBS = List.of("give");
    private static final List<String> DELETE_SUBS = List.of("all", "nearest");

    private final AirdropPlugin plugin;

    public AirdropCommand(AirdropPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        return switch (sub) {
            case "drop" -> handleDrop(sender, args);
            case "event" -> handleEvent(sender, args);
            case "reload" -> handleReload(sender, args);
            case "zones" -> handleZones(sender, args);
            case "key" -> handleKey(sender, args);
            case "top" -> handleTop(sender, args);
            case "history" -> handleHistory(sender, args);
            case "status" -> handleStatus(sender);
            case "compass" -> handleCompass(sender);
            case "replayfx" -> handleReplayFx(sender);
            case "delete" -> handleDelete(sender, args);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private void sendUsage(CommandSender sender) {
        String msg = "&6&lAirdrop Commands:\n" +
                "&e/airdrop drop [tier] [x] [z] &7- Spawn an airdrop (airdrop.admin.drop)\n" +
                "&e/airdrop event <double|eclipse|surge> [args] &7- Trigger events (airdrop.admin.event)\n" +
                "&e/airdrop reload &7- Reload config (airdrop.admin.reload)\n" +
                "&e/airdrop zones <list|add|remove> [args] &7- Manage zones (airdrop.admin.zones)\n" +
                "&e/airdrop key give <player> <tier> &7- Give key to player (airdrop.admin.keys)\n" +
                "&e/airdrop top [player] &7- View leaderboard (airdrop.use)\n" +
                "&e/airdrop history &7- View last drops (airdrop.admin.history)\n" +
                "&e/airdrop status &7- Show active drop info (airdrop.use)\n" +
                "&e/airdrop compass &7- Get compass to active drop (airdrop.use)\n" +
                "&e/airdrop replayfx &7- Replay FX at active drop (airdrop.admin.replayfx)\n" +
                "&e/airdrop delete <all|nearest|uuid> &7- Delete airdrops (airdrop.admin.delete)";
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    private boolean handleDrop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("airdrop.admin.drop")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNo permission."));
            return true;
        }

        var airdropManager = plugin.getAirdropManager();
        if (airdropManager == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cAirdrop manager not available."));
            return true;
        }

        CrateTier tier = CrateTier.TIER1;
        if (args.length >= 2) {
            tier = CrateTier.fromConfigKey(args[1]);
            if (tier == null) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&cUnknown tier. Use: " + Arrays.stream(CrateTier.values())
                                .map(CrateTier::getConfigKey)
                                .collect(Collectors.joining(", "))));
                return true;
            }
        }

        Location location;
        String worldName = plugin.getConfigManager().getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }

        if (args.length >= 4) {
            try {
                int x = Integer.parseInt(args[2]);
                int z = Integer.parseInt(args[3]);
                if (world == null) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNo world available."));
                    return true;
                }
                int y = world.getHighestBlockYAt(x, z);
                location = new Location(world, x + 0.5, y, z + 0.5);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cInvalid x or z coordinates."));
                return true;
            }
        } else if (sender instanceof Player player) {
            location = player.getLocation();
        } else {
            location = null;
            var zoneManager = plugin.getZoneManager();
            if (zoneManager != null) {
                location = zoneManager.pickDropLocation();
            }
            if (location == null && world != null) {
                var spawn = world.getSpawnLocation();
                int x = spawn.getBlockX() + (int) (Math.random() * 200 - 100);
                int z = spawn.getBlockZ() + (int) (Math.random() * 200 - 100);
                int y = world.getHighestBlockYAt(x, z);
                location = new Location(world, x + 0.5, y, z + 0.5);
            }
            if (location == null) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cCould not determine drop location."));
                return true;
            }
        }

        airdropManager.spawnAt(tier, location, false, AirdropEvent.TriggerSource.COMMAND);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&aDrop spawned: " + tier.getDisplayName()));
        return true;
    }

    private boolean handleEvent(CommandSender sender, String[] args) {
        if (!sender.hasPermission("airdrop.admin.event")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNo permission."));
            return true;
        }

        var eventDropManager = plugin.getEventDropManager();
        if (eventDropManager == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cEvent manager not available."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cUsage: /airdrop event <double|eclipse|surge> [minutes for double]"));
            return true;
        }

        String eventType = args[1].toLowerCase();

        switch (eventType) {
            case "double" -> {
                int minutes = 60;
                if (args.length >= 3) {
                    try {
                        minutes = Integer.parseInt(args[2]);
                        if (minutes < 1) minutes = 60;
                    } catch (NumberFormatException ignored) {
                    }
                }
                eventDropManager.startDoubleDrop(minutes);
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&aDouble Drop started for " + minutes + " minutes."));
            }
            case "eclipse" -> {
                Location loc = sender instanceof Player p ? p.getLocation() : null;
                eventDropManager.triggerEclipse(loc);
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aEclipse triggered."));
            }
            case "surge" -> {
                eventDropManager.triggerDropSurge();
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aDrop Surge triggered."));
            }
            default -> sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cUnknown event. Use: double, eclipse, surge"));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("airdrop.admin.reload")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNo permission."));
            return true;
        }

        var configManager = plugin.getConfigManager();
        var lootTableLoader = plugin.getLootTableLoader();
        var zoneManager = plugin.getZoneManager();

        if (configManager != null) configManager.reload();
        if (lootTableLoader != null) lootTableLoader.reload();
        if (zoneManager != null) zoneManager.reload();

        var scheduler = plugin.getAirdropScheduler();
        if (scheduler != null) {
            scheduler.stop();
            if (configManager != null && configManager.isAutoSchedule()) {
                scheduler.start();
            }
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aAirdrop config reloaded."));
        return true;
    }

    private boolean handleZones(CommandSender sender, String[] args) {
        if (!sender.hasPermission("airdrop.admin.zones")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNo permission."));
            return true;
        }

        var zoneManager = plugin.getZoneManager();
        if (zoneManager == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cZone manager not available."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cUsage: /airdrop zones <list|add|remove> [args]"));
            return true;
        }

        String sub = args[1].toLowerCase();

        switch (sub) {
            case "list" -> {
                var names = zoneManager.getZoneNames();
                if (names.isEmpty()) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7No zones defined."));
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&aZones: &f" + String.join(", ", names)));
                }
            }
            case "add" -> {
                if (args.length < 7) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&cUsage: /airdrop zones add <name> <x1> <z1> <x2> <z2>"));
                    return true;
                }
                try {
                    String name = args[2];
                    int x1 = Integer.parseInt(args[3]);
                    int z1 = Integer.parseInt(args[4]);
                    int x2 = Integer.parseInt(args[5]);
                    int z2 = Integer.parseInt(args[6]);
                    String worldName = plugin.getConfigManager().getWorldName();
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                    if (world == null) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNo world available."));
                        return true;
                    }
                    int y1 = world.getHighestBlockYAt(x1, z1);
                    int y2 = world.getHighestBlockYAt(x2, z2);
                    Location corner1 = new Location(world, x1, y1, z1);
                    Location corner2 = new Location(world, x2, y2, z2);
                    zoneManager.addZone(name, corner1, corner2);
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&aZone '" + name + "' added."));
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cInvalid coordinates."));
                }
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&cUsage: /airdrop zones remove <name>"));
                    return true;
                }
                String name = args[2];
                zoneManager.removeZone(name);
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&aZone '" + name + "' removed."));
            }
            default -> sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cUsage: /airdrop zones <list|add|remove> [args]"));
        }
        return true;
    }

    private boolean handleKey(CommandSender sender, String[] args) {
        if (!sender.hasPermission("airdrop.admin.keys")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNo permission."));
            return true;
        }

        var keyManager = plugin.getKeyManager();
        if (keyManager == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cKey manager not available."));
            return true;
        }

        if (args.length < 2 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cUsage: /airdrop key give <player> <tier>"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cUsage: /airdrop key give <player> <tier>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cPlayer not found or offline."));
            return true;
        }

        CrateTier tier = CrateTier.fromConfigKey(args[3]);
        if (tier == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cUnknown tier. Use: " + Arrays.stream(CrateTier.values())
                            .map(CrateTier::getConfigKey)
                            .collect(Collectors.joining(", "))));
            return true;
        }

        keyManager.giveKey(target, tier);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&aGave " + tier.getDisplayName() + " key to " + target.getName() + "."));
        return true;
    }

    private boolean handleTop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("airdrop.use")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNo permission."));
            return true;
        }

        var leaderboardManager = plugin.getLeaderboardManager();
        if (leaderboardManager == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cLeaderboard not available."));
            return true;
        }

        if (args.length >= 2) {
            var stats = leaderboardManager.getPlayerStats(args[1]);
            for (String line : stats) {
                sender.sendMessage(line);
            }
        } else {
            var top = leaderboardManager.getTopPlayers(10);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6&lTop 10 Airdrop Openers:"));
            for (String line : top) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f" + line));
            }
            if (top.isEmpty()) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7No data yet."));
            }
        }
        return true;
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        if (!sender.hasPermission("airdrop.admin.history")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNo permission."));
            return true;
        }

        String logFile = plugin.getConfigManager().getLogFile();
        Path path = plugin.getDataFolder().toPath().resolve(logFile);

        if (!Files.exists(path)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7No history log found."));
            return true;
        }

        try {
            long startNs = System.nanoTime();
            List<String> lines = Files.readAllLines(path);
            int size = lines.size();
            int from = Math.max(0, size - 10);
            List<String> lastLines = lines.subList(from, size);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

            // #region agent log
            DebugLogger.log(
                    "review-run-1",
                    "H1",
                    "AirdropCommand.java:handleHistory",
                    "History command file read completed",
                    new LinkedHashMap<>(java.util.Map.of(
                            "lineCount", size,
                            "elapsedMs", elapsedMs,
                            "senderType", sender.getClass().getSimpleName()
                    ))
            );
            // #endregion

            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6&lLast 10 entries:"));
            for (String line : lastLines) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&f" + line));
            }
            if (lastLines.isEmpty()) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Log is empty."));
            }
        } catch (IOException e) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cFailed to read history: " + e.getMessage()));
        }
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        if (!sender.hasPermission("airdrop.use")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNo permission."));
            return true;
        }

        var airdropManager = plugin.getAirdropManager();
        if (airdropManager == null || !airdropManager.hasActiveDrop()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7No active airdrop."));
            return true;
        }

        CrateEntity crate = airdropManager.getActiveCrate();
        CrateTier tier = crate.getTier();
        Location loc = crate.getTargetLocation();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
        String landed = crate.isLanded() ? "&aYes" : "&eNo";
        String mystery = crate.isMystery() ? " &d(Mystery)" : "";

        String msg = String.format("&6&lActive Airdrop:\n" +
                "&eTier: %s%s\n" +
                "&eWorld: &f%s\n" +
                "&eLocation: &f%d, %d\n" +
                "&eLanded: %s",
                tier.getColoredName(), mystery, world,
                loc.getBlockX(), loc.getBlockZ(), landed);
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        return true;
    }

    private boolean handleCompass(CommandSender sender) {
        if (!sender.hasPermission("airdrop.use")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNo permission."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cPlayers only."));
            return true;
        }

        var airdropManager = plugin.getAirdropManager();
        if (airdropManager == null || !airdropManager.hasActiveDrop()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7No active airdrop to track."));
            return true;
        }

        CrateEntity crate = airdropManager.getActiveCrate();
        Location target = crate.getTargetLocation();
        if (target == null || target.getWorld() == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cCould not determine drop location."));
            return true;
        }

        NamespacedKey key = new NamespacedKey(plugin, "airdrop_compass");

        ItemStack compass = null;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COMPASS && item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                compass = item;
                break;
            }
        }

        if (compass == null) {
            compass = new ItemStack(Material.COMPASS);
        }

        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e\u2699 Airdrop Tracker"));
        meta.setLodestone(target);
        meta.setLodestoneTracked(false);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        compass.setItemMeta(meta);

        if (!player.getInventory().contains(compass)) {
            player.getInventory().addItem(compass);
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&aCompass updated! Tracking " + crate.getTier().getDisplayName() + " crate at " +
                target.getBlockX() + ", " + target.getBlockZ()));
        return true;
    }

    private boolean handleReplayFx(CommandSender sender) {
        if (!sender.hasPermission("airdrop.admin.replayfx")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNo permission."));
            return true;
        }

        var airdropManager = plugin.getAirdropManager();
        if (airdropManager == null || !airdropManager.hasActiveDrop()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7No active airdrop to replay FX for."));
            return true;
        }

        CrateEntity crate = airdropManager.getActiveCrate();
        Location loc = crate.getTargetLocation();

        var visualEffects = plugin.getVisualEffects();
        if (visualEffects != null) {
            visualEffects.landingFX(crate);
            visualEffects.playWorldSpawnFlash(loc, crate.getTier());
        }

        var soundEngine = plugin.getSoundEngine();
        if (soundEngine != null) {
            soundEngine.playLandingSound(crate.getTier(), loc);
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&aReplayed FX at active drop location."));
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("airdrop.admin.delete")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNo permission."));
            return true;
        }

        var airdropManager = plugin.getAirdropManager();
        if (airdropManager == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cAirdrop manager not available."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cUsage: /airdrop delete <all|nearest|uuid>"));
            return true;
        }

        String which = args[1];
        if ("all".equalsIgnoreCase(which)) {
            int removed = airdropManager.despawnAllCrates();
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aRemoved &f" + removed + " &aairdrop(s)."));
            return true;
        }

        if ("nearest".equalsIgnoreCase(which)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cPlayers only for nearest."));
                return true;
            }
            CrateEntity nearest = airdropManager.findNearestCrate(player);
            if (nearest == null) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7No active airdrops."));
                return true;
            }
            boolean ok = airdropManager.despawnCrate(nearest.getCrateId());
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    ok ? "&aRemoved nearest airdrop: &f" + nearest.getCrateId()
                       : "&cFailed to remove nearest airdrop."));
            return true;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(which);
            boolean ok = airdropManager.despawnCrate(id);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    ok ? "&aRemoved airdrop: &f" + id : "&7No active airdrop with that UUID."));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cInvalid UUID. Usage: /airdrop delete <all|nearest|uuid>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        long startNs = System.nanoTime();
        if (args.length == 1) {
            List<String> result = filter(SUBCOMMANDS, args[0]);
            maybeLogTabComplete(sender, args, result.size(), startNs);
            return result;
        }

        String sub = args[0].toLowerCase();

        if (args.length == 2) {
            List<String> result = switch (sub) {
                case "drop" -> filter(tierConfigKeys(), args[1]);
                case "event" -> filter(EVENT_SUBS, args[1]);
                case "zones" -> filter(ZONES_SUBS, args[1]);
                case "key" -> filter(KEY_SUBS, args[1]);
                case "delete" -> filter(DELETE_SUBS, args[1]);
                case "top" -> filter(onlinePlayerNames(), args[1]);
                default -> Collections.emptyList();
            };
            maybeLogTabComplete(sender, args, result.size(), startNs);
            return result;
        }

        if (args.length == 3) {
            if ("event".equals(sub) && "double".equals(args[1].toLowerCase())) {
                List<String> result = filter(List.of("60", "30", "120"), args[2]);
                maybeLogTabComplete(sender, args, result.size(), startNs);
                return result;
            }
            if ("zones".equals(sub) && "remove".equals(args[1].toLowerCase())) {
                var zoneManager = plugin.getZoneManager();
                List<String> result = zoneManager != null ? filter(zoneManager.getZoneNames(), args[2]) : Collections.emptyList();
                maybeLogTabComplete(sender, args, result.size(), startNs);
                return result;
            }
            if ("key".equals(sub) && "give".equals(args[1].toLowerCase())) {
                List<String> result = filter(onlinePlayerNames(), args[2]);
                maybeLogTabComplete(sender, args, result.size(), startNs);
                return result;
            }
        }

        if (args.length == 4) {
            if ("key".equals(sub) && "give".equals(args[1].toLowerCase())) {
                List<String> result = filter(tierConfigKeys(), args[3]);
                maybeLogTabComplete(sender, args, result.size(), startNs);
                return result;
            }
        }

        List<String> result = Collections.emptyList();
        maybeLogTabComplete(sender, args, result.size(), startNs);
        return result;
    }

    private static void maybeLogTabComplete(CommandSender sender, String[] args, int suggestions, long startNs) {
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        if (elapsedMs < 10) {
            return;
        }
        // #region agent log
        DebugLogger.log(
                "review-run-1",
                "H5",
                "AirdropCommand.java:onTabComplete",
                "Tab completion exceeded threshold",
                new LinkedHashMap<>(java.util.Map.of(
                        "elapsedMs", elapsedMs,
                        "argsLength", args.length,
                        "suggestions", suggestions,
                        "senderType", sender.getClass().getSimpleName()
                ))
        );
        // #endregion
    }

    private static List<String> filter(List<String> options, String input) {
        if (input == null || input.isEmpty()) return new ArrayList<>(options);
        String lower = input.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    private static List<String> tierConfigKeys() {
        return Stream.of(CrateTier.values()).map(CrateTier::getConfigKey).toList();
    }

    private static List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }
}
