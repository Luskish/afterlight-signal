package org.rllabs.afterlight.gate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.rllabs.afterlight.Afterlight;

@EventBusSubscriber(modid = Afterlight.MOD_ID)
public final class VisualAcceptanceGateCore {
    private static final ResourceLocation GATE_CORE_ID =
            ResourceLocation.fromNamespaceAndPath("kubejs", "gate_of_return_core");

    private VisualAcceptanceGateCore() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (!"true".equals(System.getProperty("afterlight.visual.acceptance"))) {
            return;
        }
        event.register(
                Registries.ITEM,
                GATE_CORE_ID,
                () -> new Item(new Item.Properties().stacksTo(1)));
    }

    static ItemStack stack() {
        if (!BuiltInRegistries.ITEM.containsKey(GATE_CORE_ID)) {
            throw new IllegalStateException("Visual Gate core did not register");
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(GATE_CORE_ID));
    }
}
