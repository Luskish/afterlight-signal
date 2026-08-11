package org.rllabs.afterlight.visual;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class VisualHarnessIdentity {
    public static final String USERNAME = "AfterlightVisual";
    public static final UUID OFFLINE_UUID = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + USERNAME).getBytes(StandardCharsets.UTF_8));

    private VisualHarnessIdentity() {}

    public static boolean isExpected(GameProfile profile) {
        return profile != null
                && USERNAME.equals(profile.getName())
                && OFFLINE_UUID.equals(profile.getId());
    }
}
