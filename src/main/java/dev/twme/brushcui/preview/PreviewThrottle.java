package dev.twme.brushcui.preview;

/**
 * Per-player decision logic: when to send, re-send or clear the preview.
 *
 * <p>Not thread safe; each player's throttle is only used from that player's scheduler.</p>
 */
public final class PreviewThrottle {

    public enum Decision {
        NONE,
        SEND,
        CLEAR
    }

    private PreviewTarget lastSent;
    private boolean shown;
    private long lastSendTick = Long.MIN_VALUE / 2;
    private PreviewTarget lastSeen;
    private long lastSeenChangeTick;
    private boolean resendRequested;
    private long resendNotBeforeTick = Long.MIN_VALUE / 2;
    private double tokens = Double.NaN;
    private long lastRefillTick;

    /**
     * Decides what to do this tick.
     *
     * @param tick         current server tick
     * @param target       what should be shown, or {@code null} for nothing
     * @param cost         number of messages a send would take
     */
    public Decision decide(long tick, PreviewTarget target, UpdateMode mode, ThrottleSettings settings, int cost) {
        refill(tick, settings);

        if (target == null) {
            lastSeen = null;
            return shown ? Decision.CLEAR : Decision.NONE;
        }

        if (!target.equals(lastSeen)) {
            lastSeen = target;
            lastSeenChangeTick = tick;
        }

        boolean resend = resendRequested && tick >= resendNotBeforeTick;
        if (shown && target.equals(lastSent) && !resend) return Decision.NONE;
        if (resendRequested && !resend) return Decision.NONE;
        if (tick - lastSendTick < settings.minSendIntervalTicks()) return Decision.NONE;

        if (mode == UpdateMode.SETTLE
                && shown
                && !resend
                && target.isTranslationOf(lastSent)
                && tick - lastSeenChangeTick < settings.settleTicks()) {
            return Decision.NONE;
        }

        if (tokens < cost) return Decision.NONE;
        return Decision.SEND;
    }

    public void markSent(long tick, PreviewTarget target, int cost) {
        lastSent = target;
        shown = true;
        lastSendTick = tick;
        resendRequested = false;
        tokens -= cost;
    }

    public void markCleared() {
        lastSent = null;
        shown = false;
        tokens -= 1;
    }

    /**
     * Forces a full re-send no earlier than {@code tick + delayTicks}, for example after the
     * client re-sent its CUI handshake and dropped every multi region.
     */
    public void requestResend(long tick, int delayTicks) {
        resendRequested = true;
        resendNotBeforeTick = tick + delayTicks;
    }

    /** Forgets what the client shows, for example after it changed worlds. */
    public void reset() {
        lastSent = null;
        shown = false;
    }

    public boolean isShown() {
        return shown;
    }

    private void refill(long tick, ThrottleSettings settings) {
        double capacity = settings.maxMessagesPerSecond();
        if (Double.isNaN(tokens)) {
            tokens = capacity;
        } else if (tick > lastRefillTick) {
            tokens = Math.min(capacity, tokens + (tick - lastRefillTick) * capacity / 20.0);
        }
        lastRefillTick = tick;
    }
}
