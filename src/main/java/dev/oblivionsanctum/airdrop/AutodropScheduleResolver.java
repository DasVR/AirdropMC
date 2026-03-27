package dev.oblivionsanctum.airdrop;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolves US Eastern wall-clock windows for automatic airdrops.
 * Uses {@link ZoneId} (default {@code America/New_York}) so EST/EDT is handled correctly.
 */
public final class AutodropScheduleResolver {

    private final ZoneId zoneId;
    private final List<IrlScheduleWindow> windows;
    private final AutodropScheduleMode outsideWindowsMode;

    public AutodropScheduleResolver(
            ZoneId zoneId,
            List<IrlScheduleWindow> windows,
            AutodropScheduleMode outsideWindowsMode
    ) {
        this.zoneId = zoneId;
        this.windows = List.copyOf(windows);
        this.outsideWindowsMode = outsideWindowsMode;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public AutodropScheduleMode currentMode() {
        return modeAt(ZonedDateTime.now(zoneId));
    }

    public AutodropScheduleMode modeAt(ZonedDateTime zdt) {
        int minuteOfDay = zdt.getHour() * 60 + zdt.getMinute();
        for (IrlScheduleWindow w : windows) {
            if (w.containsMinuteOfDay(minuteOfDay)) {
                return w.getMode();
            }
        }
        return outsideWindowsMode;
    }

    /**
     * Milliseconds from {@code from} until the start of the next minute where mode is not DISABLED.
     * Capped at 48h + 1m if misconfigured (returns that cap).
     */
    public long millisUntilEarliestNonDisabled(ZonedDateTime from) {
        ZonedDateTime cursor = from.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        ZonedDateTime limit = from.plusHours(48).plusMinutes(1);

        while (cursor.isBefore(limit)) {
            if (modeAt(cursor) != AutodropScheduleMode.DISABLED) {
                return Duration.between(from, cursor).toMillis();
            }
            cursor = cursor.plusMinutes(1);
        }
        return Duration.ofHours(48).toMillis();
    }

    public static AutodropScheduleResolver fromConfig(ConfigManager config) {
        String tzName = config.getIrlScheduleTimezone();
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(tzName);
        } catch (Exception e) {
            zoneId = ZoneId.of("America/New_York");
        }

        List<IrlScheduleWindow> list = new ArrayList<>();
        List<?> raw = config.getRawIrlScheduleWindows();
        if (raw != null) {
            for (Object o : raw) {
                if (!(o instanceof java.util.Map<?, ?> map)) continue;
                Object startObj = map.get("start");
                Object endObj = map.get("end");
                Object modeObj = map.get("mode");
                if (startObj == null || endObj == null || modeObj == null) continue;
                try {
                    int sm = parseTimeToMinutes(String.valueOf(startObj).trim());
                    int em = parseTimeToMinutes(String.valueOf(endObj).trim());
                    AutodropScheduleMode mode = parseMode(String.valueOf(modeObj).trim());
                    list.add(new IrlScheduleWindow(sm, em, mode));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return new AutodropScheduleResolver(zoneId, list, config.getIrlScheduleOutsideMode());
    }

    static int parseTimeToMinutes(String s) {
        if ("24:00".equalsIgnoreCase(s)) {
            return 24 * 60;
        }
        try {
            var parts = s.split(":");
            if (parts.length != 2) throw new IllegalArgumentException();
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) throw new IllegalArgumentException();
            return h * 60 + m;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad time: " + s);
        }
    }

    static AutodropScheduleMode parseMode(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "disabled", "off" -> AutodropScheduleMode.DISABLED;
            case "peak", "most_active", "most-active" -> AutodropScheduleMode.PEAK;
            case "normal", "active" -> AutodropScheduleMode.NORMAL;
            default -> AutodropScheduleMode.NORMAL;
        };
    }
}
