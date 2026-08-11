package org.rllabs.afterlight.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.rllabs.afterlight.Afterlight;
import org.rllabs.afterlight.client.EchoScreenLayout.Rect;
import org.rllabs.afterlight.client.EchoScreenModel.Action;
import org.rllabs.afterlight.integration.EchoQuestGateway;
import org.rllabs.afterlight.route.EchoQuestSnapshot;
import org.rllabs.afterlight.route.EchoRecommendation;
import org.rllabs.afterlight.route.EchoRoute;
import org.rllabs.afterlight.route.EchoRouteResolver;

public class EchoScreen extends Screen {
    public static final int CYAN = 0x43E0D2;
    public static final int AMBER = 0xE7A64A;
    public static final int FAULT = 0xD44045;
    public static final int BONE = 0xD8D4C7;
    public static final int VAULT_BLACK = 0x030506;

    private static final int MUTATION_COOLDOWN_TICKS = 10;
    private static final ResourceLocation PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Afterlight.MOD_ID,
            "textures/gui/echo_panel.png");
    private static final Component IDENTITY = Component.translatable("screen.afterlight.echo.identity");

    private final EchoRoute route;
    private final EchoQuestGateway gateway;
    private final EchoRouteResolver resolver;
    private final EnumMap<Action, Button> actionButtons = new EnumMap<>(Action.class);
    private EchoScreenModel model;
    private Map<Long, EchoQuestSnapshot> snapshots;
    private EchoScreenLayout layout;
    private PendingMutation pendingMutation;

    public EchoScreen(EchoRoute route, EchoQuestGateway gateway) {
        this(Objects.requireNonNull(route), gateway, false);
    }

    private EchoScreen(EchoRoute route, EchoQuestGateway gateway, boolean routeUnavailable) {
        super(Component.translatable("screen.afterlight.echo.title"));
        this.route = route;
        this.gateway = Objects.requireNonNull(gateway);
        this.resolver = new EchoRouteResolver();
        this.snapshots = Map.of();
        this.model = routeUnavailable ? EchoScreenModel.routeUnavailable() : refreshModel();
    }

    public static EchoScreen signalUnavailable(EchoQuestGateway gateway) {
        return new EchoScreen(null, gateway, true);
    }

    @Override
    protected void init() {
        int framebufferWidth = minecraft.getWindow().getWidth();
        int framebufferHeight = minecraft.getWindow().getHeight();
        int guiScale = Math.max(1, (int) Math.round(minecraft.getWindow().getGuiScale()));
        layout = EchoScreenLayout.compute(framebufferWidth, framebufferHeight, guiScale);
        actionButtons.clear();
        addActionButton(Action.SUBMIT, Component.translatable("screen.afterlight.echo.action.submit"), 0);
        addActionButton(Action.CLAIM, Component.translatable("screen.afterlight.echo.action.claim"), 1);
        addActionButton(Action.PIN, model.pinLabel(), 2);
        addActionButton(Action.ARCHIVE, Component.translatable("screen.afterlight.echo.action.archive"), 3);
        updateButtons();
    }

    @Override
    public void tick() {
        if (route != null) {
            Map<Long, EchoQuestSnapshot> previousSnapshots = snapshots;
            EchoScreenModel nextModel = refreshModel();
            if (pendingMutation != null) {
                if (!previousSnapshots.equals(snapshots)) {
                    pendingMutation = null;
                } else if (pendingMutation.ticksRemaining() <= 1) {
                    pendingMutation = null;
                } else {
                    pendingMutation = new PendingMutation(
                            pendingMutation.action(),
                            pendingMutation.ticksRemaining() - 1);
                }
            }
            model = nextModel;
        }
        updateButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (layout == null) {
            return;
        }
        renderHeader(graphics);
        renderTranscript(graphics);
        renderRoute(graphics);
        renderProgress(graphics);
        if (model.action(Action.ARCHIVE).emphasized()) {
            Rect archive = layout.actionButtons().get(3);
            graphics.renderOutline(archive.x() - 1, archive.y() - 1, archive.width() + 2, archive.height() + 2, opaque(AMBER));
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, opaque(VAULT_BLACK));
        graphics.blit(PANEL_TEXTURE, 0, 0, width, height, 0.0F, 0.0F, 256, 256, 256, 256);
        graphics.fill(0, 0, width, height, 0x62030506);
        if (layout == null) {
            return;
        }
        drawPane(graphics, layout.header(), 0xD0030506, CYAN);
        drawPane(graphics, layout.transcript(), 0xDA030506, CYAN);
        drawPane(graphics, layout.route(), 0xE3030506, BONE);
        drawPane(graphics, layout.progress(), 0xE3030506, CYAN);
        drawPane(
                graphics,
                layout.actionRail(),
                0xE8030506,
                model.action(Action.ARCHIVE).emphasized() ? AMBER : BONE);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    void activate(Action action) {
        Objects.requireNonNull(action);
        if (!isActionEnabled(action)) {
            return;
        }
        switch (action) {
            case SUBMIT -> model.selectedTaskId().ifPresent(gateway::submit);
            case CLAIM -> model.selectedRewardId().ifPresent(gateway::claim);
            case PIN -> model.selectedQuestId().ifPresent(gateway::togglePin);
            case ARCHIVE -> model.selectedQuestId().ifPresent(gateway::openArchive);
        }
        if (action != Action.ARCHIVE) {
            pendingMutation = new PendingMutation(action, MUTATION_COOLDOWN_TICKS);
        }
        updateButtons();
    }

    boolean isActionEnabled(Action action) {
        return model.action(action).enabled()
                && (pendingMutation == null || pendingMutation.action() != action);
    }

    private EchoScreenModel refreshModel() {
        Map<Long, EchoQuestSnapshot> refreshed = gateway.snapshots(route);
        snapshots = Map.copyOf(refreshed);
        EchoRecommendation recommendation = resolver.resolve(route, snapshots);
        return EchoScreenModel.from(route, snapshots, recommendation);
    }

    private void addActionButton(Action action, Component label, int index) {
        Rect bounds = layout.actionButtons().get(index);
        Button button = Button.builder(label, ignored -> activate(action))
                .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height())
                .build();
        actionButtons.put(action, addRenderableWidget(button));
    }

    private void updateButtons() {
        for (Map.Entry<Action, Button> entry : actionButtons.entrySet()) {
            entry.getValue().active = isActionEnabled(entry.getKey());
        }
        Button pin = actionButtons.get(Action.PIN);
        if (pin != null) {
            pin.setMessage(model.pinLabel());
        }
    }

    private void renderHeader(GuiGraphics graphics) {
        Rect pane = layout.header();
        graphics.drawString(font, title, pane.x() + 5, pane.y() + 4, opaque(CYAN), false);
        drawWrapped(graphics, IDENTITY, pane, pane.y() + 15, BONE);
    }

    private void renderTranscript(GuiGraphics graphics) {
        Rect pane = layout.transcript();
        drawPaneLabel(graphics, pane, Component.translatable("screen.afterlight.echo.pane.transcript"), CYAN);
        int y = pane.y() + 15;
        y = drawWrapped(graphics, model.stateLabel(), pane, y, model.kind() == EchoRecommendation.Kind.SIGNAL_UNAVAILABLE ? FAULT : AMBER);
        drawWrapped(graphics, model.diagnostic(), pane, y + 3, BONE);
    }

    private void renderRoute(GuiGraphics graphics) {
        Rect pane = layout.route();
        drawPaneLabel(graphics, pane, Component.translatable("screen.afterlight.echo.pane.route"), BONE);
        int y = pane.y() + (pane.height() < 34 ? 13 : 15);
        y = drawWrapped(graphics, model.questTitle(), pane, y, CYAN);
        y = drawWrapped(graphics, model.questSubtitle(), pane, y + 2, BONE);
        if (model.selectedQuestId().isPresent() && y + 9 < pane.bottom() - 3) {
            graphics.drawString(
                    font,
                    Component.literal("Q//" + EchoRoute.formatQuestId(model.selectedQuestId().getAsLong())),
                    pane.x() + 5,
                    y + 3,
                    opaque(AMBER),
                    false);
        }
    }

    private void renderProgress(GuiGraphics graphics) {
        Rect pane = layout.progress();
        drawPaneLabel(graphics, pane, Component.translatable("screen.afterlight.echo.pane.progress"), CYAN);
        int y = pane.y() + (pane.height() < 34 ? 13 : 15);
        Component routeProgress = Component.translatable(
                "screen.afterlight.echo.route.progress",
                model.routeComplete(),
                model.routeTotal(),
                model.routePosition());
        y = drawWrapped(graphics, routeProgress, pane, y, BONE);
        if (y + 9 < pane.bottom() - 8) {
            drawWrapped(graphics, model.interactionTitle(), pane, y + 2, CYAN);
        }
        if (pane.height() >= 34) {
            int barX = pane.x() + 5;
            int barY = pane.bottom() - 8;
            int barWidth = Math.max(1, pane.width() - 10);
            graphics.fill(barX, barY, barX + barWidth, barY + 4, opaque(VAULT_BLACK));
            int filled = progressWidth(barWidth, model.currentProgress(), model.requiredProgress());
            if (filled > 0) {
                graphics.fill(barX, barY, barX + filled, barY + 4, opaque(CYAN));
            }
        }
    }

    private void drawPaneLabel(GuiGraphics graphics, Rect pane, Component label, int color) {
        graphics.drawString(font, label, pane.x() + 5, pane.y() + 4, opaque(color), false);
    }

    private int drawWrapped(GuiGraphics graphics, Component text, Rect pane, int y, int color) {
        int textWidth = Math.max(1, pane.width() - 10);
        int currentY = y;
        graphics.enableScissor(pane.x() + 2, pane.y() + 2, pane.right() - 2, pane.bottom() - 2);
        for (var line : font.split(text, textWidth)) {
            if (currentY + font.lineHeight > pane.bottom() - 3) {
                break;
            }
            graphics.drawString(font, line, pane.x() + 5, currentY, opaque(color), false);
            currentY += font.lineHeight;
        }
        graphics.disableScissor();
        return currentY;
    }

    private static void drawPane(GuiGraphics graphics, Rect pane, int background, int border) {
        graphics.fill(pane.x(), pane.y(), pane.right(), pane.bottom(), background);
        graphics.renderOutline(pane.x(), pane.y(), pane.width(), pane.height(), opaque(border));
    }

    private static int progressWidth(int width, long current, long required) {
        if (required <= 0L || current <= 0L) {
            return 0;
        }
        double ratio = Math.min(1.0D, (double) current / (double) required);
        return Math.max(1, (int) Math.round(width * ratio));
    }

    private static int opaque(int color) {
        return 0xFF000000 | color;
    }

    private record PendingMutation(Action action, int ticksRemaining) {
    }
}
