package dev.oblivionsanctum.airdrop;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Sends Discord webhook notifications using the Discord Execute Webhook API
 * with embeds so messages are visible in channels.
 */
public class DiscordHook {

    private static final Map<Integer, Integer> TIER_COLORS = Map.of(
            1, 0x999999,  // grey
            2, 0x2D7D2D,  // dark green
            3, 0xFFAA00,  // gold
            4, 0x55FFFF,  // aqua
            5, 0x00AAAA,  // dark aqua
            6, 0xFF55FF,  // light purple
            7, 0xAA0000   // dark red
    );

    private final AirdropPlugin plugin;
    private final ConfigManager configManager;
    private final HttpClient httpClient;

    public DiscordHook(AirdropPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.httpClient = HttpClient.newHttpClient();
    }

    private boolean shouldFire(String triggerId) {
        if (configManager == null || !configManager.isDiscordEnabled()) return false;
        List<String> triggers = configManager.getDiscordTriggers();
        return triggers == null || triggers.isEmpty() || triggers.contains(triggerId);
    }

    /**
     * Sends a Discord embed message via webhook.
     */
    public void sendEmbed(String title, String description, int color, JSONArray fields, String footer) {
        if (configManager == null || !configManager.isDiscordEnabled()) return;

        String webhookUrl = configManager.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) return;
        webhookUrl = webhookUrl.trim();

        try {
            JSONObject embed = new JSONObject();
            embed.put("title", title);
            if (description != null) embed.put("description", description);
            embed.put("color", color);
            embed.put("timestamp", Instant.now().toString());
            if (fields != null && fields.length() > 0) {
                embed.put("fields", fields);
            }
            if (footer != null) {
                embed.put("footer", new JSONObject().put("text", footer));
            }

            JSONObject payload = new JSONObject();
            if (configManager.isDiscordUseEmbeds()) {
                payload.put("embeds", new JSONArray().put(embed));
            } else {
                StringBuilder sb = new StringBuilder("**").append(title).append("**");
                if (description != null) sb.append("\n").append(description);
                payload.put("content", sb.toString());
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, throwable) -> {
                        if (throwable != null) {
                            plugin.getLogger().warning("Discord webhook error: " + throwable.getMessage());
                        } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            plugin.getLogger().warning("Discord webhook returned " + response.statusCode()
                                    + ": " + response.body());
                        }
                    });
        } catch (Exception e) {
            plugin.getLogger().warning("Discord webhook error: " + e.getMessage());
        }
    }

    public void fireTierLand(CrateEntity crate) {
        if (crate == null) return;
        CrateTier tier = crate.getTier();
        if (tier == null) return;

        String triggerId = tier.getConfigKey() + "_land";
        if (!shouldFire(triggerId) && !shouldFire("eclipse_all")) return;

        Location loc = crate.getTargetLocation();
        int x = loc != null ? loc.getBlockX() : 0;
        int z = loc != null ? loc.getBlockZ() : 0;
        String tierName = tier.getDisplayName();
        int color = TIER_COLORS.getOrDefault(tier.getLevel(), 0xFFFFFF);

        String title = crate.isMystery()
                ? "\u2699 Mystery Crate Incoming!"
                : "\u2699 " + tierName + " Crate Incoming!";

        JSONArray fields = new JSONArray();
        fields.put(new JSONObject().put("name", "Tier").put("value", tierName).put("inline", true));
        fields.put(new JSONObject().put("name", "Location").put("value", x + ", " + z).put("inline", true));

        sendEmbed(title, null, color, fields, configManager.getServerName());
    }

    public void fireTierClaim(CrateEntity crate, Player player, List<String> lootSummary) {
        if (crate == null) return;
        CrateTier tier = crate.getTier();
        if (tier == null) return;

        String triggerId = tier.getConfigKey() + "_claim";
        if (!shouldFire(triggerId)) return;

        String playerName = player != null ? player.getName() : "Unknown";
        int color = TIER_COLORS.getOrDefault(tier.getLevel(), 0xFFFFFF);

        String title = "\u2699 " + tier.getDisplayName() + " Crate Claimed!";

        JSONArray fields = new JSONArray();
        fields.put(new JSONObject().put("name", "Player").put("value", playerName).put("inline", true));
        fields.put(new JSONObject().put("name", "Tier").put("value", tier.getDisplayName()).put("inline", true));
        if (lootSummary != null && !lootSummary.isEmpty()) {
            fields.put(new JSONObject().put("name", "Loot").put("value", String.join(", ", lootSummary)).put("inline", false));
        }

        sendEmbed(title, null, color, fields, configManager.getServerName());
    }

    public void fireDoubleDrop(int durationMinutes) {
        if (!shouldFire("event_start")) return;

        JSONArray fields = new JSONArray();
        fields.put(new JSONObject().put("name", "Duration").put("value", durationMinutes + " minutes").put("inline", true));

        sendEmbed("\u26A1 Double Drop Activated!", "Airdrops are now falling twice as fast!",
                0xFFAA00, fields, configManager.getServerName());
    }

    public void fireEclipse(Location location) {
        if (!shouldFire("eclipse_all")) return;

        int x = location != null ? location.getBlockX() : 0;
        int z = location != null ? location.getBlockZ() : 0;

        JSONArray fields = new JSONArray();
        fields.put(new JSONObject().put("name", "Location").put("value", x + ", " + z).put("inline", true));

        sendEmbed("\uD83C\uDF11 Eclipse Event!", "The void tears open. Something falls...",
                0xAA0000, fields, configManager.getServerName());
    }

    public void fireVoteReward(String rewardType, int voteCount) {
        if (!shouldFire("vote_reward")) return;

        JSONArray fields = new JSONArray();
        fields.put(new JSONObject().put("name", "Reward").put("value", rewardType != null ? rewardType : "Unknown").put("inline", true));
        fields.put(new JSONObject().put("name", "Votes").put("value", String.valueOf(voteCount)).put("inline", true));

        sendEmbed("\uD83D\uDDF3 Vote Reward Triggered!", null, 0x55FF55, fields, configManager.getServerName());
    }

    public void fireSeasonalStart(String seasonName) {
        if (!shouldFire("seasonal_start")) return;

        sendEmbed("\uD83C\uDF83 " + (seasonName != null ? seasonName : "Seasonal") + " Event Started!",
                "A seasonal event has begun!", 0xFF8800, null, configManager.getServerName());
    }
}
