package org.rllabs.afterlight.echo;

import java.lang.ref.WeakReference;
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
    private static final Map<MinecraftServer, Map<UUID, PendingFirstIssue>> PENDING_FIRST_ISSUES = new WeakHashMap<>();

    private EchoPlayerEvents() {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.serverLevel().getServer();
        EchoBond bond = player.getExistingData(EchoContent.ECHO_BOND).orElse(EchoBond.UNISSUED);
        if (bond.issued()) {
            removePending(server, player.getUUID());
            return;
        }
        PENDING_FIRST_ISSUES
                .computeIfAbsent(server, ignored -> new HashMap<>())
                .put(player.getUUID(), new PendingFirstIssue(new WeakReference<>(player), server.getTickCount() + 1));
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.serverLevel().getServer();
        Map<UUID, PendingFirstIssue> pending = PENDING_FIRST_ISSUES.get(server);
        if (pending == null) {
            return;
        }
        pending.computeIfPresent(
                player.getUUID(),
                (ignored, issue) -> {
                    ServerPlayer pendingPlayer = issue.session().get();
                    return pendingPlayer == null || pendingPlayer == player ? null : issue;
                });
        removeEmptyServerEntry(server, pending);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Map<UUID, PendingFirstIssue> pending = PENDING_FIRST_ISSUES.get(server);
        if (pending == null) {
            return;
        }

        Iterator<Map.Entry<UUID, PendingFirstIssue>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingFirstIssue> entry = iterator.next();
            PendingFirstIssue issue = entry.getValue();
            ServerPlayer pendingPlayer = issue.session().get();
            if (pendingPlayer == null) {
                iterator.remove();
                continue;
            }
            if (server.getTickCount() < issue.dueTick()) {
                continue;
            }
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != pendingPlayer) {
                continue;
            }
            EchoBond bond = player.getExistingData(EchoContent.ECHO_BOND).orElse(EchoBond.UNISSUED);
            if (bond.issued()) {
                continue;
            }
            var result = EchoRuntimeService.INSTANCE.issueFirst(player);
            player.displayClientMessage(EchoRuntimeService.INSTANCE.resultMessage(result, true), false);
        }

        removeEmptyServerEntry(server, pending);
    }

    private static void removePending(MinecraftServer server, UUID playerId) {
        Map<UUID, PendingFirstIssue> pending = PENDING_FIRST_ISSUES.get(server);
        if (pending == null) {
            return;
        }
        pending.remove(playerId);
        removeEmptyServerEntry(server, pending);
    }

    private static void removeEmptyServerEntry(MinecraftServer server, Map<UUID, PendingFirstIssue> pending) {
        if (pending.isEmpty()) {
            PENDING_FIRST_ISSUES.remove(server);
        }
    }

    private record PendingFirstIssue(WeakReference<ServerPlayer> session, int dueTick) {
    }
}
