package dev.tentaworks.client;

/**
 * The controls hint of a hold, once learned it stays away: the action-bar line with the keys for Set Down, Throw and the hold
 * distance goes up when a Grab lands, for the first {@value #HOLDS} holds of a game session, never over another action-bar line
 * (a refusal), and is kept up for {@value #TICKS} ticks while the hold lasts and the line is still the one shown. Until then the
 * Grab key also shows beside the Grab cue. Client config {@code hud.hints} turns both off.
 */
public final class GrabHint {
    public static final int HOLDS = 2;
    /** The hint's whole time on screen, and how long one action-bar line lasts. */
    public static final int TICKS = 100, BAR_TICKS = 60;
    private int shown;
    private int since = Integer.MIN_VALUE / 2;

    /** A Grab landed at {@code tick}: the hint goes up now. {@code barBusy}: another action-bar line is showing. */
    public boolean landed(int tick, boolean enabled, boolean barBusy) {
        if (!live(enabled) || barBusy) return false;
        shown++;
        since = tick;
        return true;
    }

    /** The line is put up again at {@code tick} so it lasts {@value #TICKS} ticks: once, while holding and nothing else took the action bar. */
    public boolean renews(int tick, boolean holding, boolean stillShown) {
        return holding && stillShown && tick - since == TICKS - BAR_TICKS;
    }

    /** The wearer has not seen every hint of this session yet: the Grab key shows beside the Grab cue. */
    public boolean live(boolean enabled) {
        return enabled && shown < HOLDS;
    }

    public int shown() {
        return shown;
    }

    public void reset() {
        shown = 0;
        since = Integer.MIN_VALUE / 2;
    }
}
