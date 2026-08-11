package org.rllabs.afterlight.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.rllabs.afterlight.Afterlight;
import org.rllabs.afterlight.EchoContent;

@GameTestHolder(Afterlight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GateFieldGameTests {
    private static final String TEMPLATE = "bastion/blocks/air";

    private GateFieldGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = TEMPLATE, timeoutTicks = 30)
    public static void flowingWaterDoesNotReplaceGateField(GameTestHelper helper) {
        BlockPos fieldPosition = new BlockPos(1, 1, 1);
        BlockPos waterPosition = fieldPosition.above();
        helper.setBlock(fieldPosition, EchoContent.GATE_FIELD.get());
        helper.setBlock(waterPosition, Blocks.WATER);
        helper.getLevel().scheduleTick(helper.absolutePos(waterPosition), Fluids.WATER, 1);

        helper.runAfterDelay(10, () -> {
            helper.assertBlockPresent(EchoContent.GATE_FIELD.get(), fieldPosition);
            helper.assertTrue(
                    helper.getLevel().getFluidState(helper.absolutePos(fieldPosition)).isEmpty(),
                    "gate field accepted flowing water");
            helper.succeed();
        });
    }
}
