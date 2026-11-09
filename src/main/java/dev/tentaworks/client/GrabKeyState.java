package dev.tentaworks.client;

/**
 * The Grab key, tap against hold (CONTEXT: Grab). A press with nothing held asks for a Grab at once; that press never sets
 * down. A press while holding acts on its release: Set Down, unless the wheel moved the hold or a Throw went while the key was
 * down, then nothing. A press is the key going down, or a consumed click with the key up the tick before (a tap shorter than
 * a tick is a press and its release in one tick); the key's own repeat clicks while held are no new press.
 */
public final class GrabKeyState {
    public enum Action { NONE, GRAB, SET_DOWN }

    private boolean wasDown;
    private boolean pending;
    private boolean scrolled;
    private boolean threw;

    /** One client tick: {@code clicks} consumed this tick, {@code down} the key is held now, {@code holding} the Actuators hold something. */
    public Action tick(int clicks, boolean down, boolean holding) {
        boolean press = !wasDown && (down || clicks > 0);
        wasDown = down;
        if (!holding) pending = false;
        if (press) {
            if (!holding) return Action.GRAB;
            if (!pending) {
                pending = true;
                scrolled = false;
                threw = false;
            }
        }
        if (pending && !down) {
            pending = false;
            return scrolled || threw ? Action.NONE : Action.SET_DOWN;
        }
        return Action.NONE;
    }

    /** The wheel moved the hold while the key was down. */
    public void scrolled() {
        if (pending) scrolled = true;
    }

    /** A Throw went while the key was down. */
    public void threw() {
        if (pending) threw = true;
    }

    /** A press while holding waits for its release. */
    public boolean pending() {
        return pending;
    }

    public void reset() {
        wasDown = pending = scrolled = threw = false;
    }
}
