package org.rllabs.afterlight.gate;

import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface GateProgressGateway {
    boolean completed(ServerPlayer player, long taskId);
}
