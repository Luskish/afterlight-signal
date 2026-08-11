package org.rllabs.afterlight.client;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.gui.ModListScreen;

public final class SignalTitleScreen extends Screen {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("afterlight", "textures/gui/title.png");
    private static final int BACKGROUND_WIDTH = 1672;
    private static final int BACKGROUND_HEIGHT = 941;
    private static final int CARBON_BLACK = 0xE8050708;
    private static final int OXIDIZED_METAL = 0xFF283238;
    private static final int SIGNAL_CYAN = 0xFF4FE6F2;
    private static final int RELIQUARY_AMBER = 0xFFD09B4D;
    private static final int PALE_SIGNAL = 0xFFC7D3D4;

    SignalTitleScreen() {
        super(Component.literal("AFTERLIGHT // SIGNAL RELIQUARY"));
    }

    static List<String> menuLabels() {
        return Arrays.stream(Destination.values()).map(Destination::label).toList();
    }

    @Override
    protected void init() {
        MenuGeometry geometry = menuGeometry();
        int y = geometry.y();
        for (Destination destination : Destination.values()) {
            Button button = Button.builder(Component.literal(destination.label()), ignored -> open(destination))
                    .bounds(geometry.x(), y, geometry.width(), geometry.buttonHeight())
                    .build(SignalButton::new);
            if (destination == Destination.JOIN_EXPEDITION) {
                button.active = this.minecraft.allowsMultiplayer();
            }
            this.addRenderableWidget(button);
            y += geometry.buttonHeight() + geometry.gap();
        }
    }

    private void open(Destination destination) {
        switch (destination) {
            case SOLO_EXPEDITION -> this.minecraft.setScreen(new SelectWorldScreen(this));
            case JOIN_EXPEDITION -> this.minecraft.setScreen(this.minecraft.options.skipMultiplayerWarning
                    ? new JoinMultiplayerScreen(this)
                    : new SafetyScreen(this));
            case CONFIGURATION -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options));
            case MODS -> this.minecraft.setScreen(new ModListScreen(this));
            case DISCONNECT -> this.minecraft.stop();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderCoverBackground(graphics);
        renderReliquaryFrame(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCoverBackground(GuiGraphics graphics) {
        double viewportAspect = this.height == 0 ? 1.0 : (double) this.width / this.height;
        double textureAspect = (double) BACKGROUND_WIDTH / BACKGROUND_HEIGHT;
        int sourceX = 0;
        int sourceY = 0;
        int sourceWidth = BACKGROUND_WIDTH;
        int sourceHeight = BACKGROUND_HEIGHT;
        if (viewportAspect > textureAspect) {
            sourceHeight = Mth.clamp((int) Math.round(BACKGROUND_WIDTH / viewportAspect), 1, BACKGROUND_HEIGHT);
            sourceY = (BACKGROUND_HEIGHT - sourceHeight) / 2;
        } else {
            sourceWidth = Mth.clamp((int) Math.round(BACKGROUND_HEIGHT * viewportAspect), 1, BACKGROUND_WIDTH);
            sourceX = (BACKGROUND_WIDTH - sourceWidth) / 2;
        }
        graphics.blit(
                BACKGROUND,
                0,
                0,
                this.width,
                this.height,
                sourceX,
                sourceY,
                sourceWidth,
                sourceHeight,
                BACKGROUND_WIDTH,
                BACKGROUND_HEIGHT);
    }

    private void renderReliquaryFrame(GuiGraphics graphics) {
        MenuGeometry geometry = menuGeometry();
        int panelX = geometry.x() - 12;
        int panelY = Math.max(4, geometry.y() - 16);
        int panelRight = Math.min(this.width - 4, geometry.x() + geometry.width() + 12);
        int panelBottom = Math.min(this.height - 4, geometry.bottom() + 16);
        graphics.fill(panelX, panelY, panelRight, panelBottom, CARBON_BLACK);
        graphics.fill(panelX, panelY, panelX + 1, panelBottom, OXIDIZED_METAL);
        graphics.fill(panelX + 4, panelY + 4, panelX + 6, panelBottom - 4, SIGNAL_CYAN);
        graphics.fill(panelX + 4, panelY + 4, panelX + 6, panelY + 14, RELIQUARY_AMBER);

        if (this.width >= 360 && this.height >= 180) {
            graphics.drawString(this.font, this.getTitle(), 22, 22, PALE_SIGNAL, true);
            graphics.fill(22, 34, Math.min(214, panelX - 12), 35, SIGNAL_CYAN);
            graphics.drawString(this.font, Component.literal("ECHO CARRIER: STANDBY"), 22, 42, SIGNAL_CYAN, false);
            graphics.drawString(this.font, Component.literal("RELAY 07 / DAWN RECOVERY"), 22, this.height - 22, RELIQUARY_AMBER, false);
        }
    }

    private MenuGeometry menuGeometry() {
        int buttonHeight = this.height < 180 ? 18 : 20;
        int gap = this.height < 180 ? 2 : 4;
        int width = Mth.clamp(this.width / 3, 150, 210);
        width = Math.min(width, Math.max(80, this.width - 32));
        int x = Math.max(16, this.width - width - (this.width < 360 ? 10 : 28));
        int totalHeight = Destination.values().length * buttonHeight + (Destination.values().length - 1) * gap;
        int maximumY = Math.max(4, this.height - totalHeight - 10);
        int y = Mth.clamp((this.height - totalHeight) / 2, 4, maximumY);
        return new MenuGeometry(x, y, width, buttonHeight, gap, totalHeight);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private enum Destination {
        SOLO_EXPEDITION("Solo Expedition"),
        JOIN_EXPEDITION("Join Expedition"),
        CONFIGURATION("Configuration"),
        MODS("Mods"),
        DISCONNECT("Disconnect");

        private final String label;

        Destination(String label) {
            this.label = label;
        }

        String label() {
            return this.label;
        }
    }

    private record MenuGeometry(int x, int y, int width, int buttonHeight, int gap, int totalHeight) {
        int bottom() {
            return this.y + this.totalHeight;
        }
    }

    private static final class SignalButton extends Button {
        private SignalButton(Builder builder) {
            super(builder);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int border = this.isHoveredOrFocused() ? SIGNAL_CYAN : OXIDIZED_METAL;
            int foreground = this.active ? PALE_SIGNAL : 0xFF6D787B;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), border);
            graphics.fill(
                    this.getX() + 1,
                    this.getY() + 1,
                    this.getX() + this.getWidth() - 1,
                    this.getY() + this.getHeight() - 1,
                    CARBON_BLACK);
            if (this.isHoveredOrFocused()) {
                graphics.fill(this.getX() + 3, this.getY() + 3, this.getX() + 5, this.getY() + this.getHeight() - 3, RELIQUARY_AMBER);
            }
            this.renderString(graphics, Minecraft.getInstance().font, foreground);
        }
    }
}
