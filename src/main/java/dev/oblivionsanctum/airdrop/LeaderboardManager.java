package dev.oblivionsanctum.airdrop;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages airdrop leaderboard stats via SQLite.
 */
public class LeaderboardManager {

    private final AirdropPlugin plugin;
    private Connection connection;

    public LeaderboardManager(AirdropPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the SQLite connection and creates the stats table if leaderboard is enabled.
     */
    public void init() {
        ConfigManager config = plugin.getConfigManager();
        if (!config.isLeaderboardEnabled()) {
            return;
        }

        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File dbFile = new File(dataFolder, config.getDbFile());
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS airdrop_stats (
                        uuid TEXT PRIMARY KEY,
                        name TEXT,
                        opens_t1 INTEGER DEFAULT 0,
                        opens_t2 INTEGER DEFAULT 0,
                        opens_t3 INTEGER DEFAULT 0,
                        opens_t4 INTEGER DEFAULT 0,
                        opens_t5 INTEGER DEFAULT 0,
                        first_opens INTEGER DEFAULT 0,
                        xp_earned INTEGER DEFAULT 0,
                        rarest_item TEXT,
                        last_seen TEXT
                    )
                    """);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize leaderboard database: " + e.getMessage());
        }
    }

    /**
     * Records a crate open for the given player and tier.
     */
    public void recordOpen(Player player, CrateTier tier) {
        if (connection == null) return;

        try {
            String uuid = player.getUniqueId().toString();
            String name = player.getName();
            int level = tier.getLevel();
            if (level < 1 || level > 5) return;

            String opensCol = "opens_t" + level;
            String datetime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT OR IGNORE INTO airdrop_stats (uuid, name) VALUES (?, ?)")) {
                insert.setString(1, uuid);
                insert.setString(2, name);
                insert.executeUpdate();
            }

            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE airdrop_stats SET " + opensCol + " = " + opensCol + " + 1, " +
                            "last_seen = ?, name = ? WHERE uuid = ?")) {
                update.setString(1, datetime);
                update.setString(2, name);
                update.setString(3, uuid);
                update.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to record open: " + e.getMessage());
        }
    }

    /**
     * Records a first-open bonus for the player.
     */
    public void recordFirstOpen(Player player) {
        if (connection == null) return;

        try {
            String uuid = player.getUniqueId().toString();
            String name = player.getName();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT OR IGNORE INTO airdrop_stats (uuid, name) VALUES (?, ?)")) {
                insert.setString(1, uuid);
                insert.setString(2, name);
                insert.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE airdrop_stats SET first_opens = first_opens + 1 WHERE uuid = ?")) {
                ps.setString(1, uuid);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to record first open: " + e.getMessage());
        }
    }

    /**
     * Records XP earned for the player.
     */
    public void recordXp(Player player, int amount) {
        if (connection == null) return;

        try {
            String uuid = player.getUniqueId().toString();
            String name = player.getName();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT OR IGNORE INTO airdrop_stats (uuid, name) VALUES (?, ?)")) {
                insert.setString(1, uuid);
                insert.setString(2, name);
                insert.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE airdrop_stats SET xp_earned = xp_earned + ? WHERE uuid = ?")) {
                ps.setInt(1, amount);
                ps.setString(2, uuid);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to record XP: " + e.getMessage());
        }
    }

    /**
     * Returns the total number of crate opens for the player.
     */
    public int getTotalOpens(OfflinePlayer player) {
        if (connection == null) return 0;

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT opens_t1 + opens_t2 + opens_t3 + opens_t4 + opens_t5 FROM airdrop_stats WHERE uuid = ?")) {
            ps.setString(1, player.getUniqueId().toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get total opens: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Returns the name of the player with the most total opens.
     */
    public String getTopPlayer() {
        if (connection == null) return "";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM airdrop_stats ORDER BY opens_t1 + opens_t2 + opens_t3 + opens_t4 + opens_t5 DESC LIMIT 1")) {
            return rs.next() ? rs.getString("name") : "";
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get top player: " + e.getMessage());
            return "";
        }
    }

    /**
     * Returns the top players formatted as "1. PlayerName - 42 opens".
     */
    public List<String> getTopPlayers(int limit) {
        List<String> result = new ArrayList<>();
        if (connection == null) return result;

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name, opens_t1 + opens_t2 + opens_t3 + opens_t4 + opens_t5 AS total " +
                        "FROM airdrop_stats ORDER BY total DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    String name = rs.getString("name");
                    int total = rs.getInt("total");
                    result.add(rank + ". " + name + " - " + total + " opens");
                    rank++;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get top players: " + e.getMessage());
        }
        return result;
    }

    /**
     * Returns formatted stat card lines for the given player name.
     */
    public List<String> getPlayerStats(String playerName) {
        List<String> result = new ArrayList<>();
        if (connection == null) return result;

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM airdrop_stats WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    result.add(ChatColor.GRAY + "No stats found for " + playerName);
                    return result;
                }

                result.add(ChatColor.GOLD + "--- " + rs.getString("name") + "'s Airdrop Stats ---");
                result.add(ChatColor.YELLOW + "Total Opens: " + ChatColor.WHITE +
                        (rs.getInt("opens_t1") + rs.getInt("opens_t2") + rs.getInt("opens_t3") +
                                rs.getInt("opens_t4") + rs.getInt("opens_t5")));
                result.add(ChatColor.YELLOW + "First Opens: " + ChatColor.WHITE + rs.getInt("first_opens"));
                result.add(ChatColor.YELLOW + "XP Earned: " + ChatColor.WHITE + rs.getInt("xp_earned"));
                String rarest = rs.getString("rarest_item");
                if (rarest != null && !rarest.isEmpty()) {
                    result.add(ChatColor.YELLOW + "Rarest Item: " + ChatColor.WHITE + rarest);
                }
                String lastSeen = rs.getString("last_seen");
                if (lastSeen != null && !lastSeen.isEmpty()) {
                    result.add(ChatColor.YELLOW + "Last Seen: " + ChatColor.WHITE + lastSeen);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get player stats: " + e.getMessage());
            result.add(ChatColor.RED + "Error loading stats.");
        }
        return result;
    }

    /**
     * Closes the database connection.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to close leaderboard connection: " + e.getMessage());
            }
            connection = null;
        }
    }
}
