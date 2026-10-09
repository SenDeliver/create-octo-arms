package dev.tentaworks.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipFramesTest {
    @Test
    void withoutSableNoSableClassIsNeeded() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("dev.ryanhcode.sable.companion.SableCompanion"));
        assertSame(NoShips.INSTANCE, ShipFrames.get());
    }
}
