package org.rllabs.afterlight.client;

import com.mojang.authlib.minecraft.BanDetails;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.sounds.SoundManager;
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
    private final ClientAccess client;

    SignalTitleScreen() {
        this(new MinecraftClientAccess());
    }

    SignalTitleScreen(ClientAccess client) {
        super(Component.literal("AFTERLIGHT // SIGNAL RELIQUARY"));
        this.client = client;
    }

    @Override
    protected void init() {
        MenuGeometry geometry = menuGeometry();
        Component multiplayerDisabledReason = multiplayerDisabledReason();
        int y = geometry.y();
        for (Destination destination : Destination.values()) {
            Button button = Button.builder(Component.literal(destination.label()), ignored -> open(destination))
                    .bounds(geometry.x(), y, geometry.width(), geometry.buttonHeight())
                    .build(SignalButton::new);
            if (destination == Destination.JOIN_EXPEDITION) {
                button.active = multiplayerDisabledReason == null;
                button.setTooltip(multiplayerDisabledReason == null ? null : Tooltip.create(multiplayerDisabledReason));
            }
            this.addRenderableWidget(button);
            y += geometry.buttonHeight() + geometry.gap();
        }
    }

    private void open(Destination destination) {
        switch (destination) {
            case SOLO_EXPEDITION -> this.client.setScreen(new SelectWorldScreen(this));
            case JOIN_EXPEDITION -> this.client.setScreen(this.client.skipMultiplayerWarning()
                    ? new JoinMultiplayerScreen(this)
                    : new SafetyScreen(this));
            case CONFIGURATION -> this.client.setScreen(new OptionsScreen(this, this.client.options()));
            case MODS -> this.client.setScreen(new ModListScreen(this));
            case DISCONNECT -> this.client.stop();
        }
    }

    @Nullable
    private Component multiplayerDisabledReason() {
        if (this.client.allowsMultiplayer()) {
            return null;
        }
        if (this.client.isNameBanned()) {
            return Component.translatable("title.multiplayer.disabled.banned.name");
        }
        BanDetails banDetails = this.client.multiplayerBan();
        if (banDetails == null) {
            return Component.translatable("title.multiplayer.disabled");
        }
        return Component.translatable(banDetails.expires() == null
                ? "title.multiplayer.disabled.banned.permanent"
                : "title.multiplayer.disabled.banned.temporary");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderCoverBackground(graphics);
        renderReliquaryFrame(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCoverBackground(GuiGraphics graphics) {
        CoverCrop crop = coverCrop(this.width, this.height);
        graphics.blit(
                BACKGROUND,
                0,
                0,
                this.width,
                this.height,
                crop.sourceX(),
                crop.sourceY(),
                crop.sourceWidth(),
                crop.sourceHeight(),
                BACKGROUND_WIDTH,
                BACKGROUND_HEIGHT);
    }

    static CoverCrop coverCrop(int viewportWidth, int viewportHeight) {
        double viewportAspect = viewportHeight == 0 ? 1.0 : (double) viewportWidth / viewportHeight;
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
        return new CoverCrop(sourceX, sourceY, sourceWidth, sourceHeight);
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
        return menuGeometry(this.width, this.height);
    }

    static MenuGeometry menuGeometry(int viewportWidth, int viewportHeight) {
        int buttonHeight = viewportHeight < 180 ? 18 : 20;
        int gap = viewportHeight < 180 ? 2 : 4;
        int width = Mth.clamp(viewportWidth / 3, 150, 210);
        width = Math.min(width, Math.max(80, viewportWidth - 32));
        int x = Math.max(16, viewportWidth - width - (viewportWidth < 360 ? 10 : 28));
        int totalHeight = Destination.values().length * buttonHeight + (Destination.values().length - 1) * gap;
        int maximumY = Math.max(4, viewportHeight - totalHeight - 10);
        int y = Mth.clamp((viewportHeight - totalHeight) / 2, 4, maximumY);
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

    private record CoverCrop(int sourceX, int sourceY, int sourceWidth, int sourceHeight) {
    }

    private record ButtonDecoration(int border, boolean amberRail) {
    }

    private static final class SignalButton extends Button {
        private SignalButton(Builder builder) {
            super(builder);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ButtonDecoration decoration = decoration();
            int foreground = this.active ? PALE_SIGNAL : 0xFF6D787B;
            graphics.fill(
                    this.getX(),
                    this.getY(),
                    this.getX() + this.getWidth(),
                    this.getY() + this.getHeight(),
                    decoration.border());
            graphics.fill(
                    this.getX() + 1,
                    this.getY() + 1,
                    this.getX() + this.getWidth() - 1,
                    this.getY() + this.getHeight() - 1,
                    CARBON_BLACK);
            if (decoration.amberRail()) {
                graphics.fill(this.getX() + 3, this.getY() + 3, this.getX() + 5, this.getY() + this.getHeight() - 3, RELIQUARY_AMBER);
            }
            this.renderString(graphics, Minecraft.getInstance().font, foreground);
        }

        private ButtonDecoration decoration() {
            boolean activeHighlight = this.active && this.isHoveredOrFocused();
            return new ButtonDecoration(activeHighlight ? SIGNAL_CYAN : OXIDIZED_METAL, activeHighlight);
        }

        @Override
        public void playDownSound(@Nullable SoundManager soundManager) {
            if (soundManager != null) {
                super.playDownSound(soundManager);
            }
        }
    }

    interface ClientAccess {
        boolean allowsMultiplayer();

        boolean isNameBanned();

        @Nullable
        BanDetails multiplayerBan();

        boolean skipMultiplayerWarning();

        Options options();

        void setScreen(Screen screen);

        void stop();
    }

    private static final class MinecraftClientAccess implements ClientAccess {
        @Override
        public boolean allowsMultiplayer() {
            return Minecraft.getInstance().allowsMultiplayer();
        }

        @Override
        public boolean isNameBanned() {
            return Minecraft.getInstance().isNameBanned();
        }

        @Override
        @Nullable
        public BanDetails multiplayerBan() {
            return Minecraft.getInstance().multiplayerBan();
        }

        @Override
        public boolean skipMultiplayerWarning() {
            return Minecraft.getInstance().options.skipMultiplayerWarning;
        }

        @Override
        public Options options() {
            return Minecraft.getInstance().options;
        }

        @Override
        public void setScreen(Screen screen) {
            Minecraft.getInstance().setScreen(screen);
        }

        @Override
        public void stop() {
            Minecraft.getInstance().stop();
        }
    }
}
