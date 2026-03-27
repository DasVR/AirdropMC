package dev.oblivionsanctum.airdrop;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.ZonedDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wraps BukkitScheduler to manage automatic airdrop scheduling.
 * Optional real-world (wall-clock) windows use US Eastern by default via {@link AutodropScheduleResolver}.
 */
public class AirdropScheduler {

    private final AirdropPlugin plugin;
    private final ConfigManager configManager;
    private AirdropManager airdropManager;
    private int taskId = -1;
    private long nextDropTime;
    private boolean doubleDropActive;

    public AirdropScheduler(AirdropPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void setAirdropManager(AirdropManager manager) {
        this.airdropManager = manager;
    }

    public void start() {
        if (!configManager.isAutoSchedule()) {
            return;
        }
        scheduleNext();
        taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() < nextDropTime) {
                    return;
                }

                if (configManager.isRequirePlayersOnline()
                        && Bukkit.getOnlinePlayers().isEmpty()) {
                    return;
                }

                if (configManager.isIrlScheduleEnabled()) {
                    AutodropScheduleResolver resolver = AutodropScheduleResolver.fromConfig(configManager);
                    AutodropScheduleMode mode = resolver.currentMode();
                    if (mode == AutodropScheduleMode.DISABLED) {
                        scheduleNext();
                        return;
                    }
                    if (airdropManager != null) {
                        airdropManager.triggerAuto();
                    }
                    scheduleNext();
                    return;
                }

                if (airdropManager != null) {
                    airdropManager.triggerAuto();
                }
                scheduleNext();
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();
    }

    public void scheduleNext() {
        if (configManager.isIrlScheduleEnabled()) {
            scheduleNextIrl();
            return;
        }

        int min = configManager.getMinInterval();
        int max = configManager.getMaxInterval();
        if (doubleDropActive) {
            min = Math.max(1, min / 2);
            max = Math.max(min, max / 2);
        }
        if (max < min) max = min;
        int seconds = min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        nextDropTime = System.currentTimeMillis() + (seconds * 1000L);
    }

    private void scheduleNextIrl() {
        AutodropScheduleResolver resolver = AutodropScheduleResolver.fromConfig(configManager);
        ZonedDateTime now = ZonedDateTime.now(resolver.getZoneId());
        AutodropScheduleMode mode = resolver.modeAt(now);

        if (mode == AutodropScheduleMode.DISABLED) {
            long delayMs = resolver.millisUntilEarliestNonDisabled(now);
            nextDropTime = System.currentTimeMillis() + Math.max(1000L, delayMs);
            return;
        }

        int min;
        int max;
        if (mode == AutodropScheduleMode.PEAK) {
            min = configManager.getIrlPeakMinInterval();
            max = configManager.getIrlPeakMaxInterval();
        } else {
            min = configManager.getMinInterval();
            max = configManager.getMaxInterval();
        }

        if (doubleDropActive) {
            min = Math.max(1, min / 2);
            max = Math.max(min, max / 2);
        }

        if (max < min) max = min;
        int seconds = min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        nextDropTime = System.currentTimeMillis() + (seconds * 1000L);
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public void restart() {
        stop();
        start();
    }

    public void setDoubleDropActive(boolean active) {
        this.doubleDropActive = active;
        if (taskId != -1) {
            scheduleNext();
        }
    }

    public long getSecondsUntilNext() {
        return Math.max(0, (nextDropTime - System.currentTimeMillis()) / 1000);
    }

    public String getNextDropCountdown() {
        long seconds = getSecondsUntilNext();
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }
}
