package dev.oblivionsanctum.airdrop;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion for airdrop-related placeholders.
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final AirdropPlugin plugin;

    public PlaceholderHook(AirdropPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "airdrop";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Arriq";
    }

    @Override
    public @NotNull String getVersion() {
        return "2.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (plugin == null) return "";

        try {
            return switch (params) {
                case "next" -> {
                    var scheduler = plugin.getAirdropScheduler();
                    yield scheduler != null ? scheduler.getNextDropCountdown() : "";
                }
                case "active" -> {
                    var airdropManager = plugin.getAirdropManager();
                    yield airdropManager != null ? String.valueOf(airdropManager.hasActiveDrop()) : "false";
                }
                case "active_tier" -> {
                    var airdropManager = plugin.getAirdropManager();
                    yield airdropManager != null ? airdropManager.getActiveTierName() : "";
                }
                case "active_coords" -> {
                    var airdropManager = plugin.getAirdropManager();
                    yield airdropManager != null ? airdropManager.getActiveCoordsString() : "";
                }
                case "total_opens" -> {
                    var leaderboardManager = plugin.getLeaderboardManager();
                    yield leaderboardManager != null && player != null
                            ? String.valueOf(leaderboardManager.getTotalOpens(player))
                            : "0";
                }
                case "top_player" -> {
                    var leaderboardManager = plugin.getLeaderboardManager();
                    yield leaderboardManager != null ? leaderboardManager.getTopPlayer() : "";
                }
                case "event_active" -> {
                    var eventDropManager = plugin.getEventDropManager();
                    yield eventDropManager != null ? String.valueOf(eventDropManager.isEventActive()) : "false";
                }
                case "event_name" -> {
                    var eventDropManager = plugin.getEventDropManager();
                    String name = eventDropManager != null ? eventDropManager.getActiveEventName() : null;
                    yield name != null ? name : "";
                }
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }
}
