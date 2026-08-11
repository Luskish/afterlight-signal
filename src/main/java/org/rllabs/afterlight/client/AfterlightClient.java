package org.rllabs.afterlight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.rllabs.afterlight.Afterlight;
import org.rllabs.afterlight.network.AfterlightPayloads;
import org.rllabs.afterlight.network.OpenEchoScreen;

@Mod(value = Afterlight.MOD_ID, dist = Dist.CLIENT)
public final class AfterlightClient {
    public AfterlightClient() {
        AfterlightPayloads.installClientOpenHandler(AfterlightClient::openPlaceholder);
    }

    private static void openPlaceholder(OpenEchoScreen payload) {
        Minecraft.getInstance().setScreen(new SignalReliquaryPlaceholder());
    }

    private static final class SignalReliquaryPlaceholder extends Screen {
        private static final Component BODY = Component.translatable("screen.afterlight.echo.placeholder.body");

        private SignalReliquaryPlaceholder() {
            super(Component.translatable("screen.afterlight.echo.placeholder.title"));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, title, width / 2, height / 2 - 10, 0xFFFFFF);
            graphics.drawCenteredString(font, BODY, width / 2, height / 2 + 10, 0xA0D8FF);
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
