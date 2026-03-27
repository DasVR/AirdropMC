package dev.oblivionsanctum.airdrop;

import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;

public enum CrateTier {

    TIER1("Salvage",    1, ChatColor.GRAY,       "tier1", false, BarColor.WHITE),
    TIER2("Patchwork",  2, ChatColor.DARK_GREEN,  "tier2", false, BarColor.GREEN),
    TIER3("Ironclad",   3, ChatColor.GOLD,        "tier3", false, BarColor.YELLOW),
    TIER4("Aetheric",   4, ChatColor.AQUA,        "tier4", true,  BarColor.BLUE),
    TIER5("Brass",      5, ChatColor.DARK_AQUA,   "tier5", true,  BarColor.BLUE),
    TIER6("Sovereign",  6, ChatColor.LIGHT_PURPLE, "tier6", true,  BarColor.PURPLE),
    TIER7("Eclipse",    7, ChatColor.DARK_RED,     "tier7", true,  BarColor.RED);

    private final String displayName;
    private final int level;
    private final ChatColor color;
    private final String configKey;
    private final boolean requiresKey;
    private final BarColor bossBarColor;

    CrateTier(String displayName, int level, ChatColor color, String configKey, boolean requiresKey, BarColor bossBarColor) {
        this.displayName = displayName;
        this.level = level;
        this.color = color;
        this.configKey = configKey;
        this.requiresKey = requiresKey;
        this.bossBarColor = bossBarColor;
    }

    public String getDisplayName() { return displayName; }
    public int getLevel() { return level; }
    public ChatColor getColor() { return color; }
    public String getConfigKey() { return configKey; }
    public boolean requiresKey() { return requiresKey; }
    public BarColor getBossBarColor() { return bossBarColor; }

    public String getColoredName() {
        return color + "[" + displayName + " Crate]";
    }

    public boolean isHighTier() {
        return level >= 5;
    }

    public static CrateTier fromConfigKey(String key) {
        for (CrateTier tier : values()) {
            if (tier.configKey.equalsIgnoreCase(key)) return tier;
        }
        return null;
    }

    public static CrateTier fromLevel(int level) {
        for (CrateTier tier : values()) {
            if (tier.level == level) return tier;
        }
        return null;
    }
}
