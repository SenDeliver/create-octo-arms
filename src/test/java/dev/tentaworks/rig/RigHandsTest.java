package dev.tentaworks.rig;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Carried counts as on the ground for mining speed and sword sweeps; Falling and Stowed keep vanilla's answer. */
class RigHandsTest {
    @Test
    void carriedCountsAsOnGround() {
        for (RigState s : new RigState[]{RigState.ACTIVE, RigState.SETTING_DOWN, RigState.CRAWL}) {
            assertTrue(RigHands.onGround(false, s), s.toString());
            assertTrue(RigHands.onGround(true, s), s.toString());
        }
    }

    @Test
    void stowedAndFallingKeepVanilla() {
        for (RigState s : new RigState[]{RigState.STOWED, RigState.FALLING}) {
            assertFalse(RigHands.onGround(false, s), s.toString());
            assertTrue(RigHands.onGround(true, s), s.toString());
        }
    }
}
