package dev.oblivionsanctum.airdrop;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class AirdropPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private LootTableLoader lootTableLoader;
    private AirdropManager airdropManager;
    private AirdropScheduler airdropScheduler;
    private ZoneManager zoneManager;
    private HeatmapTracker heatmapTracker;
    private VisualEffects visualEffects;
    private SoundEngine soundEngine;
    private RaceTracker raceTracker;
    private ClaimManager claimManager;
    private EventDropManager eventDropManager;
    private FlareManager flareManager;
    private KeyManager keyManager;
    private LeaderboardManager leaderboardManager;
    private DebrisManager debrisManager;
    private DiscordHook discordHook;
    private PlaceholderHook placeholderHook;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.loadAll();

        saveLootDefaults();

        lootTableLoader = new LootTableLoader(this, configManager);
        lootTableLoader.loadAll();

        zoneManager = new ZoneManager(this);
        zoneManager.loadZones();

        heatmapTracker = new HeatmapTracker(this);
        heatmapTracker.init();

        visualEffects = new VisualEffects(this);
        soundEngine = new SoundEngine(this);
        keyManager = new KeyManager(this);
        keyManager.registerRecipes();
        debrisManager = new DebrisManager(this);
        claimManager = new ClaimManager(this);

        discordHook = new DiscordHook(this, configManager);

        leaderboardManager = new LeaderboardManager(this);
        leaderboardManager.init();

        airdropManager = new AirdropManager(this, configManager);
        eventDropManager = new EventDropManager(this, configManager);
        raceTracker = new RaceTracker(this);

        airdropScheduler = new AirdropScheduler(this, configManager);
        airdropScheduler.setAirdropManager(airdropManager);

        flareManager = new FlareManager(this, configManager);
        flareManager.registerRecipe();

        getServer().getPluginManager().registerEvents(new CrateListener(this), this);
        getServer().getPluginManager().registerEvents(flareManager, this);

        PluginCommand cmd = getCommand("airdrop");
        if (cmd != null) {
            AirdropCommand airdropCmd = new AirdropCommand(this);
            cmd.setExecutor(airdropCmd);
            cmd.setTabCompleter(airdropCmd);
        }

        if (configManager.isPlaceholderApiEnabled() && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderHook = new PlaceholderHook(this);
            placeholderHook.register();
            getLogger().info("PlaceholderAPI hook registered.");
        }

        eventDropManager.checkSeasonalWindows();

        airdropScheduler.start();

        getLogger().info("AirdropSystem v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (airdropScheduler != null) airdropScheduler.stop();
        if (visualEffects != null) visualEffects.stopAllGlows();
        if (soundEngine != null) soundEngine.stopAll();
        if (raceTracker != null) raceTracker.stopAll();
        if (eventDropManager != null) eventDropManager.stopAll();
        if (debrisManager != null) debrisManager.cleanupAll();
        if (heatmapTracker != null) heatmapTracker.save();
        if (leaderboardManager != null) leaderboardManager.close();

        if (placeholderHook != null) placeholderHook.unregister();

        getLogger().info("AirdropSystem disabled.");
    }

    private void saveLootDefaults() {
        String[] lootFiles = {
            "loot/tier1_salvage.yml",
            "loot/tier2_patchwork.yml",
            "loot/tier3_ironclad.yml",
            "loot/tier4_aetheric.yml",
            "loot/tier5_brass.yml",
            "loot/tier6_sovereign.yml",
            "loot/tier7_eclipse.yml",
            "loot/seasonal/halloween.yml"
        };
        for (String path : lootFiles) {
            if (!new java.io.File(getDataFolder(), path).exists()) {
                saveResource(path, false);
            }
        }
    }

    public ConfigManager getConfigManager() { return configManager; }
    public LootTableLoader getLootTableLoader() { return lootTableLoader; }
    public AirdropManager getAirdropManager() { return airdropManager; }
    public AirdropScheduler getAirdropScheduler() { return airdropScheduler; }
    public ZoneManager getZoneManager() { return zoneManager; }
    public HeatmapTracker getHeatmapTracker() { return heatmapTracker; }
    public VisualEffects getVisualEffects() { return visualEffects; }
    public SoundEngine getSoundEngine() { return soundEngine; }
    public RaceTracker getRaceTracker() { return raceTracker; }
    public ClaimManager getClaimManager() { return claimManager; }
    public EventDropManager getEventDropManager() { return eventDropManager; }
    public FlareManager getFlareManager() { return flareManager; }
    public KeyManager getKeyManager() { return keyManager; }
    public LeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    public DebrisManager getDebrisManager() { return debrisManager; }
    public DiscordHook getDiscordHook() { return discordHook; }
}
