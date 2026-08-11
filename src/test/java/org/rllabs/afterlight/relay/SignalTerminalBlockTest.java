package org.rllabs.afterlight.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.EchoContent;

class SignalTerminalBlockTest {
    @Test
    void bothTerminalsExposeSafeDirectionalActiveStateDefaults() {
        for (SignalTerminalBlock terminal : new SignalTerminalBlock[] {
            EchoContent.RETURN_TERMINAL.get(), EchoContent.FUTURE_CONSOLE.get()
        }) {
            assertTrue(terminal.defaultBlockState().hasProperty(SignalTerminalBlock.FACING));
            assertTrue(terminal.defaultBlockState().hasProperty(SignalTerminalBlock.ACTIVE));
            assertEquals(
                    Direction.NORTH,
                    terminal.defaultBlockState().getValue(SignalTerminalBlock.FACING));
            assertFalse(terminal.defaultBlockState().getValue(SignalTerminalBlock.ACTIVE));
        }
    }
}
