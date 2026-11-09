package dev.tentaworks.client;

import dev.tentaworks.client.GrabKeyState.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Grab key: a press Grabs, a tap while holding Sets Down on release, a scroll or a Throw while held does nothing more. */
class GrabKeyStateTest {
    @Test
    void aPressWithNothingHeldGrabsAtOnceAndItsReleaseDoesNothing() {
        GrabKeyState k = new GrabKeyState();
        assertEquals(Action.GRAB, k.tick(1, true, false));
        assertEquals(Action.NONE, k.tick(0, true, false));
        assertEquals(Action.NONE, k.tick(0, true, true), "the hold began while the key is still down");
        assertEquals(Action.NONE, k.tick(0, false, true), "the press that started the Grab never sets down");
    }

    @Test
    void aTapWhileHoldingSetsDownOnRelease() {
        GrabKeyState k = new GrabKeyState();
        assertEquals(Action.NONE, k.tick(1, true, true));
        assertEquals(Action.NONE, k.tick(0, true, true));
        assertEquals(Action.SET_DOWN, k.tick(0, false, true));
        assertEquals(Action.NONE, k.tick(0, false, true));
    }

    @Test
    void aClickShorterThanATickIsPressAndRelease() {
        GrabKeyState k = new GrabKeyState();
        assertEquals(Action.SET_DOWN, k.tick(1, false, true));
        assertEquals(Action.GRAB, k.tick(1, false, false));
    }

    @Test
    void theKeyGoingDownCountsWithoutAClick() {
        GrabKeyState k = new GrabKeyState();
        assertEquals(Action.GRAB, k.tick(0, true, false));
        assertEquals(Action.NONE, k.tick(1, true, false), "key repeat while held is no new press");
        assertEquals(Action.NONE, k.tick(1, true, true), "nor once holding");
        assertEquals(Action.NONE, k.tick(0, false, true), "so the release sets nothing down");
    }

    @Test
    void scrollingOrThrowingWhileHeldMeansNoSetDown() {
        GrabKeyState k = new GrabKeyState();
        k.tick(1, true, true);
        k.scrolled();
        assertEquals(Action.NONE, k.tick(0, false, true));
        k.tick(1, true, true);
        k.threw();
        assertEquals(Action.NONE, k.tick(0, false, true));
        k.tick(1, true, true);
        assertEquals(Action.SET_DOWN, k.tick(0, false, true), "a fresh press is unscrolled again");
    }

    @Test
    void theHoldEndingWhileTheKeyIsDownDropsThePress() {
        GrabKeyState k = new GrabKeyState();
        k.tick(1, true, true);
        assertEquals(Action.NONE, k.tick(0, true, false));
        assertEquals(Action.NONE, k.tick(0, false, false));
    }

    @Test
    void scrollingWithTheGrabPressHeldIsHarmless() {
        GrabKeyState k = new GrabKeyState();
        k.tick(1, true, false);
        k.scrolled();
        assertEquals(Action.NONE, k.tick(0, true, true));
        assertEquals(Action.NONE, k.tick(0, false, true));
    }
}
