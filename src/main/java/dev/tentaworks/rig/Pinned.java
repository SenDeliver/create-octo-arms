package dev.tentaworks.rig;

import java.util.UUID;

/**
 * A Pinned mob (attachment {@code tentaworks:pinned}): held by the wearer {@code holder}, or set down and calm until game time
 * {@code calmUntil} (0 while held).
 */
public record Pinned(UUID holder, long calmUntil) {
    public boolean held() {
        return calmUntil == 0;
    }

    public boolean calm(long gameTime) {
        return held() || gameTime < calmUntil;
    }
}
