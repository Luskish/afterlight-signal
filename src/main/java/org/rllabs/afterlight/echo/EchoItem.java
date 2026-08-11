package org.rllabs.afterlight.echo;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.rllabs.afterlight.network.OpenEchoRequest;

public final class EchoItem extends Item {
    public EchoItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            PacketDistributor.sendToServer(new OpenEchoRequest(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
