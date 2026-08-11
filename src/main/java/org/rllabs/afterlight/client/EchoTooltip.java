package org.rllabs.afterlight.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.rllabs.afterlight.EchoContent;
import org.rllabs.afterlight.echo.EchoBond;
import org.rllabs.afterlight.echo.EchoIdentity;

final class EchoTooltip {
    private EchoTooltip() {
    }

    static void onTooltip(ItemTooltipEvent event) {
        if (!event.getItemStack().is(EchoContent.ECHO.get())) {
            return;
        }
        EchoIdentity identity = event.getItemStack().get(EchoContent.ECHO_IDENTITY.get());
        if (identity == null) {
            return;
        }
        Player player = event.getEntity();
        boolean ownedByPlayer = player != null && identity.owner().equals(player.getUUID());
        EchoBond bond = ownedByPlayer
                ? player.getExistingData(EchoContent.ECHO_BOND).orElse(EchoBond.UNISSUED)
                : EchoBond.UNISSUED;
        String owner = ownedByPlayer ? player.getName().getString() : identity.owner().toString();
        event.getToolTip().addAll(presentation(identity, bond, owner));
    }

    static List<Component> presentation(EchoIdentity identity, EchoBond bond, String owner) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip.afterlight.echo.identity").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.afterlight.echo.owner", owner).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.afterlight.echo.generation", identity.generation())
                .withStyle(ChatFormatting.GOLD));
        if (bond.issued() && bond.generation() > 0 && bond.generation() != identity.generation()) {
            lines.add(Component.translatable("tooltip.afterlight.echo.superseded").withStyle(ChatFormatting.RED));
        }
        return List.copyOf(lines);
    }
}
