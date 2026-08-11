package org.rllabs.afterlight.echo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.echo.EchoRecoveryService.RecoveryResult;
import org.rllabs.afterlight.echo.EchoRecoveryService.RecoveryStatus;

public final class EchoCommands {
    public static final int RECOVER_PERMISSION_LEVEL = 0;
    public static final int INSPECT_PERMISSION_LEVEL = 2;

    private EchoCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("echo")
                .then(Commands.literal("recover")
                        .requires(source -> source.hasPermission(RECOVER_PERMISSION_LEVEL))
                        .executes(context -> recover(context.getSource())))
                .then(Commands.literal("inspect")
                        .requires(source -> source.hasPermission(INSPECT_PERMISSION_LEVEL))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> inspect(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "player"))))));
    }

    private static int recover(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RecoveryResult result = EchoRuntimeService.INSTANCE.recover(player);
        Component message = EchoRuntimeService.INSTANCE.resultMessage(result, false);
        if (result.status() == RecoveryStatus.ISSUED) {
            source.sendSuccess(() -> message, false);
            return 1;
        }
        source.sendFailure(message);
        return 0;
    }

    private static int inspect(CommandSourceStack source, ServerPlayer player) {
        EchoBond bond = player.getExistingData(EchoContent.ECHO_BOND).orElse(EchoBond.UNISSUED);
        source.sendSuccess(
                () -> Component.translatable(
                        "message.afterlight.echo.inspect",
                        player.getGameProfile().getName(),
                        bond.issued(),
                        bond.generation(),
                        bond.issuedAtEpochSecond()),
                false);
        return 1;
    }
}
