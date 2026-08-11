package org.rllabs.afterlight.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VisualHarnessIdentityTest {
    @Test
    void onlyTheExactExpectedOfflineIdentityIsAuthorized() {
        assertEquals("AfterlightVisual", VisualHarnessIdentity.USERNAME);
        assertEquals(
                UUID.fromString("dc97e483-a961-33ef-b1b2-1948184e48c7"),
                VisualHarnessIdentity.OFFLINE_UUID);
        assertTrue(VisualHarnessIdentity.isExpected(new GameProfile(
                VisualHarnessIdentity.OFFLINE_UUID, VisualHarnessIdentity.USERNAME)));
        assertFalse(VisualHarnessIdentity.isExpected(new GameProfile(
                UUID.randomUUID(), VisualHarnessIdentity.USERNAME)));
        assertFalse(VisualHarnessIdentity.isExpected(new GameProfile(
                VisualHarnessIdentity.OFFLINE_UUID, "UnexpectedPlayer")));
        assertFalse(VisualHarnessIdentity.isExpected(null));
    }
}
