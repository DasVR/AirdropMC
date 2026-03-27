package dev.oblivionsanctum.airdrop;

/**
 * One wall-clock window [startMinute, endMinute) in a 24h cycle, end exclusive.
 * Minutes are 0..1440 where 1440 means end-of-day (24:00).
 * Overnight windows use start &gt; end (e.g. 23:00–01:00).
 */
public final class IrlScheduleWindow {

    private final int startMinute;
    private final int endMinute;
    private final AutodropScheduleMode mode;

    public IrlScheduleWindow(int startMinute, int endMinute, AutodropScheduleMode mode) {
        this.startMinute = clampDay(startMinute);
        this.endMinute = clampDay(endMinute);
        this.mode = mode;
    }

    private static int clampDay(int m) {
        return Math.max(0, Math.min(1440, m));
    }

    public boolean containsMinuteOfDay(int minuteOfDay) {
        int m = minuteOfDay % 1440;
        if (m < 0) m += 1440;
        if (startMinute == endMinute) return false;
        if (startMinute < endMinute) {
            return m >= startMinute && m < endMinute;
        }
        return m >= startMinute || m < endMinute;
    }

    public AutodropScheduleMode getMode() {
        return mode;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public int getEndMinute() {
        return endMinute;
    }
}
