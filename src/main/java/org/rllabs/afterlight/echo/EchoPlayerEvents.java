package org.rllabs.afterlight.echo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.rllabs.afterlight.EchoContent;

public final class EchoPlayerEvents {
    private static final Map<MinecraftServer, Map<UUID, Integer>> PENDING_FIRST_ISSUES = new WeakHashMap<>();

    private EchoPlayerEvents() {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EchoBond bond = player.getExistingData(EchoContent.ECHO_BOND).orElse(EchoBond.UNISSUED);
        if (bond.issued()) {
            return;
        }
        MinecraftServer server = player.serverLevel().getServer();
        PENDING_FIRST_ISSUES
                .computeIfAbsent(server, ignored -> new HashMap<>())
                .putIfAbsent(player.getUUID(), server.getTickCount() + 1);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Map<UUID, Integer> pending = PENDING_FIRST_ISSUES.get(server);
        if (pending == null) {
            return;
        }

        Iterator<Map.Entry<UUID, Integer>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            if (server.getTickCount() < entry.getValue()) {
                continue;
            }
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            EchoBond bond = player.getExistingData(EchoContent.ECHO_BOND).orElse(EchoBond.UNISSUED);
            if (bond.issued()) {
                continue;
            }
            var result = EchoRuntimeService.INSTANCE.issueFirst(player);
            player.displayClientMessage(EchoRuntimeService.INSTANCE.resultMessage(result, true), false);
        }

        if (pending.isEmpty()) {
            PENDING_FIRST_ISSUES.remove(server);
        }
    }
}
