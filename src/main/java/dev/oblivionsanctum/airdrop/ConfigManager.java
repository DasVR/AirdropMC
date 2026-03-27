package dev.oblivionsanctum.airdrop;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class ConfigManager {

    private final AirdropPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration eventsConfig;
    private FileConfiguration zonesConfig;

    public ConfigManager(AirdropPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        saveDefaultResource("events.yml");
        saveDefaultResource("zones.yml");

        eventsConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "events.yml"));
        zonesConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "zones.yml"));
    }

    public void reload() {
        loadAll();
    }

    private void saveDefaultResource(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }

    // --- Core airdrop settings ---

    public boolean isAutoSchedule() { return config.getBoolean("airdrop.auto-schedule", true); }
    public boolean isRequirePlayersOnline() { return config.getBoolean("airdrop.require-players-online", true); }
    public int getMinInterval() { return config.getInt("airdrop.min-interval", 1800); }
    public int getMaxInterval() { return config.getInt("airdrop.max-interval", 3600); }
    public String getWorldName() { return config.getString("airdrop.world", "world"); }
    public boolean isFallbackRandomCoords() { return config.getBoolean("airdrop.fallback-random-coords", true); }
    public int getSpawnAltitudeOffset() { return config.getInt("airdrop.spawn-altitude-offset", 80); }
    public boolean isAnnounceOnSchedule() { return config.getBoolean("airdrop.announce-on-schedule", true); }
    public boolean isLogDrops() { return config.getBoolean("airdrop.log-drops", true); }
    public String getLogFile() { return config.getString("airdrop.log-file", "history.log"); }
    public boolean isWorldGuardIntegration() { return config.getBoolean("airdrop.worldguard-integration", true); }

    /** "zones", "world-border", or "both" */
    public String getLocationMode() { return config.getString("airdrop.location-mode", "world-border"); }
    public int getWorldBorderMaxAttempts() { return config.getInt("airdrop.world-border.max-attempts", 50); }
    /** Max radius from border center for drop placement. 0 = unlimited (use full border). */
    public int getWorldBorderMaxRadius() { return config.getInt("airdrop.world-border.max-radius", 1500); }
    public boolean isWorldBorderAvoidWater() { return config.getBoolean("airdrop.world-border.avoid-water", true); }
    public boolean isWorldBorderAvoidLava() { return config.getBoolean("airdrop.world-border.avoid-lava", true); }
    public int getWorldBorderMinDistFromSpawn() { return config.getInt("airdrop.world-border.min-distance-from-spawn", 100); }
    public int getWorldBorderPadding() { return config.getInt("airdrop.world-border.padding", 50); }

    /**
     * When true, automatic airdrops respect {@code airdrop.schedule} using real-world time
     * in {@link #getIrlScheduleTimezone()} (default US Eastern: {@code America/New_York}).
     */
    public boolean isIrlScheduleEnabled() {
        return config.getBoolean("airdrop.schedule.enabled", false);
    }

    /** IANA timezone id, e.g. {@code America/New_York} for US Eastern (EST/EDT). */
    public String getIrlScheduleTimezone() {
        return config.getString("airdrop.schedule.timezone", "America/New_York");
    }

    /** Peak window min interval between auto drops (seconds). */
    public int getIrlPeakMinInterval() {
        return Math.max(60, config.getInt("airdrop.schedule.peak-min-interval", 600));
    }

    /** Peak window max interval between auto drops (seconds). */
    public int getIrlPeakMaxInterval() {
        return Math.max(getIrlPeakMinInterval(), config.getInt("airdrop.schedule.peak-max-interval", 1200));
    }

    /**
     * Mode when current Eastern time is not inside any listed window.
     */
    public AutodropScheduleMode getIrlScheduleOutsideMode() {
        String raw = config.getString("airdrop.schedule.outside-windows-mode", "disabled");
        return AutodropScheduleResolver.parseMode(raw != null ? raw : "disabled");
    }

    public List<Map<?, ?>> getRawIrlScheduleWindows() {
        return config.getMapList("airdrop.schedule.windows");
    }

    // --- Tier weights ---

    public Map<CrateTier, Integer> getTierWeights() {
        Map<CrateTier, Integer> weights = new EnumMap<>(CrateTier.class);
        for (CrateTier tier : CrateTier.values()) {
            weights.put(tier, config.getInt("airdrop.tier-weights." + tier.getConfigKey(), 0));
        }
        return weights;
    }

    public int getTierCooldown(CrateTier tier) {
        return config.getInt("airdrop.tier-cooldowns." + tier.getConfigKey(), 0);
    }

    public List<String> getKeyedTiers() {
        return config.getStringList("airdrop.keyed-tiers");
    }

    public boolean isTierKeyed(CrateTier tier) {
        return getKeyedTiers().contains(tier.getConfigKey());
    }

    // --- Key recipes ---

    public boolean isKeyRecipesEnabled() {
        return config.getBoolean("key-recipes.enabled", true);
    }

    public java.util.Set<String> getKeyRecipeTiers() {
        var section = config.getConfigurationSection("key-recipes.recipes");
        if (section == null) return java.util.Collections.emptySet();
        return section.getKeys(false);
    }

    public List<String> getKeyRecipeIngredients(CrateTier tier) {
        return config.getStringList("key-recipes.recipes." + tier.getConfigKey());
    }

    // --- Competition ---

    public boolean isRaceTracker() { return config.getBoolean("competition.race-tracker", true); }
    public boolean isSalvagerBonus() { return config.getBoolean("competition.salvager-bonus", true); }
    public boolean isProximityBossbar() { return config.getBoolean("competition.proximity-bossbar", true); }
    public int getProximityRadius() { return config.getInt("competition.proximity-radius", 12); }
    public boolean isSquadSplit() { return config.getBoolean("competition.squad-split", true); }

    public List<String> getSquadSplitTiers() {
        return config.getStringList("competition.squad-split-tiers");
    }

    public boolean isSquadSplitTier(CrateTier tier) {
        return getSquadSplitTiers().contains(tier.getConfigKey());
    }

    public int getSalvagerXp(CrateTier tier) {
        return config.getInt("competition.salvager-xp." + tier.getConfigKey(), tier.getLevel() * 25);
    }

    // --- Leaderboard ---

    public boolean isLeaderboardEnabled() { return config.getBoolean("leaderboard.enabled", true); }
    public String getLeaderboardStorage() { return config.getString("leaderboard.storage", "sqlite"); }
    public String getDbFile() { return config.getString("leaderboard.db-file", "airdrop_stats.db"); }

    // --- Flares ---

    public boolean isFlaresEnabled() { return config.getBoolean("flares.enabled", true); }
    public String getFlareResultTier() { return config.getString("flares.result_tier", "tier2"); }
    public int getFlareCooldown() { return config.getInt("flares.cooldown_seconds", 1800); }
    public boolean isFlareAnnounce() { return config.getBoolean("flares.announce_to_server", true); }
    public boolean isFlareRequireZone() { return config.getBoolean("flares.require_zone", false); }
    public List<String> getFlareRecipe() { return config.getStringList("flares.recipe"); }

    // --- Mystery / Defence ---

    public double getMysteryChance() { return config.getDouble("mystery_crate.chance", 0.05); }
    public boolean isCrateDefenceEnabled() { return config.getBoolean("crate_defence.enabled", true); }
    public List<String> getCrateDefenceTiers() { return config.getStringList("crate_defence.tiers"); }
    public double getCrateDefenceMobScale() { return config.getDouble("crate_defence.mob-count-scale", 1.0); }

    public boolean isCrateDefenceTier(CrateTier tier) {
        return getCrateDefenceTiers().contains(tier.getConfigKey());
    }

    // --- Guard behavior ---

    public double getGuardDefendRadius() {
        return config.getDouble("crate_defence.guard-behavior.defend-radius", 12.0);
    }

    public double getGuardAggroRadius() {
        return config.getDouble("crate_defence.guard-behavior.aggro-radius", 16.0);
    }

    public int getGuardBehaviorTickInterval() {
        return config.getInt("crate_defence.guard-behavior.tick-interval", 20);
    }

    // --- Guard mob pools ---

    public Map<String, Integer> getGuardMobPool(CrateTier tier) {
        Map<String, Integer> pool = new LinkedHashMap<>();
        var section = config.getConfigurationSection(
                "crate_defence.guard-mob-pools." + tier.getConfigKey() + ".mobs");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                pool.put(key, section.getInt(key, 1));
            }
        }
        return pool;
    }

    public double getGuardJockeyChance(CrateTier tier) {
        return config.getDouble(
                "crate_defence.guard-mob-pools." + tier.getConfigKey() + ".jockey-chance", 0.0);
    }

    // --- Guard stats ---

    public double getGuardHealthMultiplier(CrateTier tier) {
        return config.getDouble(
                "crate_defence.guard-stats." + tier.getConfigKey() + ".health-multiplier", 1.0);
    }

    public double getGuardDamageMultiplier(CrateTier tier) {
        return config.getDouble(
                "crate_defence.guard-stats." + tier.getConfigKey() + ".damage-multiplier", 1.0);
    }

    public double getGuardSpeedMultiplier(CrateTier tier) {
        return config.getDouble(
                "crate_defence.guard-stats." + tier.getConfigKey() + ".speed-multiplier", 1.0);
    }

    // --- Guard armor ---

    public String getGuardArmorHelmet(CrateTier tier) {
        return config.getString(
                "crate_defence.guard-armor." + tier.getConfigKey() + ".helmet", "IRON_HELMET");
    }

    public String getGuardArmorChestplate(CrateTier tier) {
        return config.getString(
                "crate_defence.guard-armor." + tier.getConfigKey() + ".chestplate", "");
    }

    public String getGuardArmorLeggings(CrateTier tier) {
        return config.getString(
                "crate_defence.guard-armor." + tier.getConfigKey() + ".leggings", "");
    }

    public String getGuardArmorBoots(CrateTier tier) {
        return config.getString(
                "crate_defence.guard-armor." + tier.getConfigKey() + ".boots", "");
    }

    public float getGuardArmorDropChance(CrateTier tier) {
        return (float) config.getDouble(
                "crate_defence.guard-armor." + tier.getConfigKey() + ".drop-chance", 0.0);
    }

    public List<String> getGuardArmorEnchantments(CrateTier tier) {
        return config.getStringList(
                "crate_defence.guard-armor." + tier.getConfigKey() + ".enchantments");
    }

    // --- Debris ---

    public boolean isDebrisEnabled() { return config.getBoolean("debris.enabled", true); }
    public int getDebrisDuration() { return config.getInt("debris.duration_seconds", 600); }

    // --- Sirens ---

    public boolean isSirensEnabled() { return config.getBoolean("sirens.enabled", true); }
    public List<String> getSirenTiers() { return config.getStringList("sirens.tiers"); }
    public int getSirenInterval() { return config.getInt("sirens.interval_ticks", 600); }

    public boolean isSirenTier(CrateTier tier) {
        return getSirenTiers().contains(tier.getConfigKey());
    }

    // --- Heatmap ---

    public boolean isHeatmapEnabled() { return config.getBoolean("heatmap.enabled", true); }
    public String getHeatmapFile() { return config.getString("heatmap.data-file", "heatmap.dat"); }

    // --- Discord ---

    public boolean isDiscordEnabled() { return config.getBoolean("discord.enabled", false); }
    public String getWebhookUrl() { return config.getString("discord.webhook-url", ""); }
    public String getServerName() { return config.getString("discord.server-name", "Oblivion Sanctum"); }
    public List<String> getDiscordTriggers() { return config.getStringList("discord.triggers"); }
    public boolean isDiscordUseEmbeds() { return config.getBoolean("discord.use-embeds", true); }

    // --- PlaceholderAPI ---

    public boolean isPlaceholderApiEnabled() { return config.getBoolean("placeholderapi.enabled", true); }

    // --- Vote ---

    public boolean isVoteEnabled() { return config.getBoolean("vote-integration.enabled", false); }
    public String getVotePlugin() { return config.getString("vote-integration.plugin", "VotingPlugin"); }
    public int getVoteThreshold() { return config.getInt("vote-integration.threshold", 50); }
    public int getVoteWindow() { return config.getInt("vote-integration.window_minutes", 60); }
    public String getVoteReward() { return config.getString("vote-integration.reward", "drop_surge"); }

    // --- Effects ---

    public double getParticleDensity() { return config.getDouble("effects.particle-density", 1.0); }
    public int getTrailIntervalTicks() { return config.getInt("effects.trail-interval-ticks", 2); }
    public int getBeaconIntervalTicks() { return config.getInt("effects.beacon-interval-ticks", 5); }
    public int getActiveGlowIntervalTicks() { return config.getInt("effects.active-glow-interval-ticks", 10); }

    // --- Sounds ---

    public double getSpawnSoundRadius() { return config.getDouble("sounds.spawn-radius", 500.0); }
    public int getFallLoopIntervalTicks() { return config.getInt("sounds.fall-loop-interval-ticks", 15); }
    public double getLandingSoundRadius() { return config.getDouble("sounds.landing-radius", 200.0); }
    public double getOpenSoundRadius() { return config.getDouble("sounds.open-radius", 50.0); }

    // --- Sound layers ---

    public boolean isLandingWorldCueEnabled() { return config.getBoolean("sounds.landing-world-cue-enabled", true); }
    public float getLandingWorldVolume() { return (float) config.getDouble("sounds.landing-world-volume", 0.3); }
    public int getLandingWorldMinTier() { return config.getInt("sounds.landing-world-min-tier", 4); }
    public boolean isLandingServerWideEnabled() { return config.getBoolean("sounds.landing-server-wide-enabled", true); }
    public float getLandingServerWideVolume() { return (float) config.getDouble("sounds.landing-server-wide-volume", 0.15); }
    public int getLandingServerWideMinTier() { return config.getInt("sounds.landing-server-wide-min-tier", 6); }
    public List<String> getServerWideExcludeWorlds() { return config.getStringList("sounds.server-wide-exclude-worlds"); }
    public boolean isSpawnWorldCueEnabled() { return config.getBoolean("sounds.spawn-world-cue-enabled", true); }
    public float getSpawnWorldCueVolume() { return (float) config.getDouble("sounds.spawn-world-cue-volume", 0.25); }
    public boolean isSpawnServerWideEnabled() { return config.getBoolean("sounds.spawn-server-wide-enabled", false); }
    public float getSpawnServerWideVolume() { return (float) config.getDouble("sounds.spawn-server-wide-volume", 0.1); }
    public int getSpawnServerWideMinTier() { return config.getInt("sounds.spawn-server-wide-min-tier", 6); }

    // --- Boss bar ---

    public int getBossbarUpdateIntervalTicks() { return config.getInt("bossbar.update-interval-ticks", 60); }
    public int getBossbarClaimedLingerTicks() { return config.getInt("bossbar.claimed-linger-ticks", 60); }
    public boolean isBossbarIncomingTimeoutEnabled() { return config.getBoolean("bossbar.incoming-timeout.enabled", true); }
    public int getBossbarIncomingTimeoutMinutes() { return Math.max(1, config.getInt("bossbar.incoming-timeout.minutes", 30)); }
    public String getBossbarIncomingTimeoutMessage() {
        return config.getString(
                "bossbar.incoming-timeout.message",
                "&eAirdrop is taking too long to land. Coords: &f{x}&7, &f{z} &7(&f{tier}&7)"
        );
    }

    // --- Boss bar format ---

    public boolean isBossbarShowWorld() { return config.getBoolean("bossbar.show-world-in-title", false); }
    public String getBossbarTitleIncoming() { return config.getString("bossbar.title-incoming", "&e\u2699 AIRDROP INCOMING \u2014 {x}, {z} \u2014 {tier} \u2699"); }
    public String getBossbarTitleLanded() { return config.getString("bossbar.title-landed", "&c\u2699 AIRDROP LANDED \u2014 {x}, {z} \u2014 {tier} \u2699"); }
    public String getBossbarTitleClaimed() { return config.getString("bossbar.title-claimed", "&a\u2699 CLAIMED by {player} \u2014 {x}, {z} \u2699"); }

    // --- Player-biased drop placement (closer to players) ---

    public boolean isPlayerBiasEnabled() { return config.getBoolean("airdrop.player-bias.enabled", false); }
    public int getPlayerBiasMinRadius() { return Math.max(0, config.getInt("airdrop.player-bias.min-radius", 200)); }
    public int getPlayerBiasMaxRadius() { return Math.max(getPlayerBiasMinRadius(), config.getInt("airdrop.player-bias.max-radius", 500)); }
    public int getPlayerBiasMaxAttempts() { return Math.max(1, config.getInt("airdrop.player-bias.max-attempts", 40)); }

    // --- Zoning / location search ---

    public int getZoneMaxAttemptsPerDrop() { return config.getInt("zoning.max-attempts-per-drop", 40); }
    public int getZoneMinDistanceFromPlayers() { return config.getInt("zoning.min-distance-from-players", 200); }
    public boolean isZoneAllowCaveSpawns() { return config.getBoolean("zoning.allow-cave-spawns", true); }
    public double getZoneCaveSpawnChance() { return config.getDouble("zoning.cave-spawn-chance", 0.25); }
    public int getZoneCaveScanStepY() { return Math.max(1, config.getInt("zoning.cave-scan-step-y", 2)); }
    public int getZoneCaveMinY() { return config.getInt("zoning.cave-min-y", 20); }

    // --- Unclaimed timeout ---

    public boolean isUnclaimedTimeoutEnabled() { return config.getBoolean("unclaimed-timeout.enabled", true); }
    public int getUnclaimedTimeoutMinutes() { return config.getInt("unclaimed-timeout.minutes", 15); }
    public String getUnclaimedTimeoutMessage() { return config.getString("unclaimed-timeout.message", "&7The airdrop was lost to the void..."); }

    // --- First-arrive bonus ---

    public boolean isFirstArriveBonusEnabled() { return config.getBoolean("first-arrive-bonus.enabled", true); }
    public int getFirstArriveBonusXp() { return config.getInt("first-arrive-bonus.xp", 100); }
    public int getFirstArriveBonusRadius() { return config.getInt("first-arrive-bonus.radius", 5); }
    public String getFirstArriveBonusMessage() { return config.getString("first-arrive-bonus.message", "&a{player} arrived first! (+{xp} XP)"); }

    // --- Announce formats ---

    public String getAnnounceFormat(CrateTier tier) {
        return config.getString("announce-format." + tier.getConfigKey(), "&7An airdrop has landed!");
    }

    public String getMysteryFormat() {
        return config.getString("announce-format.mystery", "&d&l[???] A mysterious crate falls... somewhere.");
    }

    public String getSalvagerFormat() {
        return config.getString("announce-format.salvager", "&a{player} claimed the Salvager Bonus! (+{xp} XP)");
    }

    public String getClaimedFormat() {
        return config.getString("announce-format.claimed", "&e{player} opened the {tier} crate!");
    }

    // --- Events config ---

    public FileConfiguration getEventsConfig() { return eventsConfig; }
    public FileConfiguration getZonesConfig() { return zonesConfig; }

    // --- Seasonal ---

    public List<Map<?, ?>> getSeasonalEvents() {
        return eventsConfig.getMapList("seasonal");
    }

    public int getDoubleDropVoteThreshold() {
        return eventsConfig.getInt("double_drop.vote_threshold", 50);
    }

    public int getDoubleDropDefaultDuration() {
        return eventsConfig.getInt("double_drop.default_duration", 60);
    }
}
