package org.rllabs.afterlight.client;

import java.util.EnumMap;
import java.util.HashMap;
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
import org.rllabs.afterlight.route.EchoQuestSnapshot.RewardSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.TaskSnapshot;
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
    private final Map<MutationKey, PendingMutation> pendingMutations = new HashMap<>();
    private EchoScreenModel model;
    private Map<Long, EchoQuestSnapshot> snapshots;
    private boolean snapshotsTrusted;
    private EchoScreenLayout layout;

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
        if (layout.mode() == EchoScreenLayout.Mode.MINIMAL) {
            return;
        }
        addActionButton(Action.SUBMIT, Component.translatable("screen.afterlight.echo.action.submit"), 0);
        addActionButton(Action.CLAIM, Component.translatable("screen.afterlight.echo.action.claim"), 1);
        addActionButton(Action.PIN, model.pinLabel(), 2);
        addActionButton(Action.ARCHIVE, Component.translatable("screen.afterlight.echo.action.archive"), 3);
        updateButtons();
    }

    @Override
    public void tick() {
        if (route != null) {
            model = refreshModel();
            advancePendingMutations();
        }
        updateButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (layout == null) {
            return;
        }
        if (layout.mode() == EchoScreenLayout.Mode.MINIMAL) {
            renderMinimalFault(graphics);
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
        if (layout.mode() == EchoScreenLayout.Mode.MINIMAL) {
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
        OptionalLong targetId = targetId(action);
        if (targetId.isEmpty()) {
            return;
        }
        long exactTargetId = targetId.getAsLong();
        MutationFingerprint fingerprint = fingerprint(action, exactTargetId);
        if (!fingerprint.exists()) {
            return;
        }
        switch (action) {
            case SUBMIT -> gateway.submit(exactTargetId);
            case CLAIM -> gateway.claim(exactTargetId);
            case PIN -> gateway.togglePin(exactTargetId);
            case ARCHIVE -> gateway.openArchive(exactTargetId);
        }
        if (action != Action.ARCHIVE) {
            pendingMutations.put(
                    new MutationKey(action, exactTargetId),
                    new PendingMutation(
                            fingerprint,
                            MUTATION_COOLDOWN_TICKS));
        }
        updateButtons();
    }

    boolean isActionEnabled(Action action) {
        return model.action(action).enabled()
                && !isPendingForCurrentTarget(action);
    }

    private EchoScreenModel refreshModel() {
        try {
            Map<Long, EchoQuestSnapshot> normalized = normalizeSnapshots(gateway.snapshots(route));
            snapshotsTrusted = isCompleteTrustedSnapshot(normalized);
            snapshots = snapshotsTrusted ? normalized : Map.of();
        } catch (RuntimeException exception) {
            snapshots = Map.of();
            snapshotsTrusted = false;
        }
        EchoRecommendation recommendation = resolver.resolve(route, snapshots);
        return EchoScreenModel.from(route, snapshots, recommendation);
    }

    private void advancePendingMutations() {
        if (pendingMutations.isEmpty()) {
            return;
        }
        pendingMutations.replaceAll((key, pending) -> pending.age());
        pendingMutations.entrySet().removeIf(entry -> entry.getValue().ticksRemaining() <= 0);
        if (!snapshotsTrusted) {
            return;
        }
        pendingMutations.entrySet().removeIf(entry -> {
            MutationKey key = entry.getKey();
            MutationFingerprint synchronizedFingerprint = fingerprint(key.action(), key.targetId());
            return !synchronizedFingerprint.exists()
                    || !entry.getValue().fingerprint().equals(synchronizedFingerprint);
        });
    }

    private boolean isPendingForCurrentTarget(Action action) {
        OptionalLong currentTarget = targetId(action);
        return currentTarget.isPresent()
                && pendingMutations.containsKey(new MutationKey(action, currentTarget.getAsLong()));
    }

    private OptionalLong targetId(Action action) {
        return switch (action) {
            case SUBMIT -> model.selectedTaskId();
            case CLAIM -> model.selectedRewardId();
            case PIN, ARCHIVE -> model.selectedQuestId();
        };
    }

    private MutationFingerprint fingerprint(Action action, long targetId) {
        return switch (action) {
            case PIN, ARCHIVE -> {
                EchoQuestSnapshot quest = snapshots.get(targetId);
                boolean exists = quest != null && quest.questId() == targetId;
                yield new PinFingerprint(exists, exists && quest.pinned());
            }
            case SUBMIT -> {
                TaskSnapshot task = findTask(targetId);
                yield task == null
                        ? SubmitFingerprint.MISSING
                        : new SubmitFingerprint(
                                true,
                                task.currentValue(),
                                task.requiredValue(),
                                task.complete(),
                                task.directInteractionSupported(),
                                task.submitEligible());
            }
            case CLAIM -> {
                RewardSnapshot reward = findReward(targetId);
                yield reward == null
                        ? ClaimFingerprint.MISSING
                        : new ClaimFingerprint(
                                true,
                                reward.claimed(),
                                reward.directInteractionSupported(),
                                reward.choice(),
                                reward.claimEligible());
            }
        };
    }

    private TaskSnapshot findTask(long targetId) {
        return snapshots.values().stream()
                .flatMap(snapshot -> snapshot.tasks().stream())
                .filter(task -> task.id() == targetId)
                .findFirst()
                .orElse(null);
    }

    private RewardSnapshot findReward(long targetId) {
        return snapshots.values().stream()
                .flatMap(snapshot -> snapshot.rewards().stream())
                .filter(reward -> reward.id() == targetId)
                .findFirst()
                .orElse(null);
    }

    private static Map<Long, EchoQuestSnapshot> normalizeSnapshots(Map<Long, EchoQuestSnapshot> rawSnapshots) {
        if (rawSnapshots == null) {
            return Map.of();
        }
        for (Map.Entry<Long, EchoQuestSnapshot> entry : rawSnapshots.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                return Map.of();
            }
        }
        return Map.copyOf(rawSnapshots);
    }

    private boolean isCompleteTrustedSnapshot(Map<Long, EchoQuestSnapshot> candidateSnapshots) {
        for (long questId : route.questIds()) {
            EchoQuestSnapshot snapshot = candidateSnapshots.get(questId);
            if (snapshot == null || snapshot.questId() != questId) {
                return false;
            }
        }
        return true;
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
        drawClippedLine(graphics, title, pane, pane.y() + 4, CYAN);
        drawWrapped(graphics, IDENTITY, pane, pane.y() + 15, BONE);
    }

    private void renderTranscript(GuiGraphics graphics) {
        Rect pane = layout.transcript();
        drawPaneLabel(graphics, pane, Component.translatable(layout.paneLabels().transcriptKey()), CYAN);
        int y = pane.y() + 15;
        y = drawWrapped(graphics, model.stateLabel(), pane, y, model.kind() == EchoRecommendation.Kind.SIGNAL_UNAVAILABLE ? FAULT : AMBER);
        drawWrapped(graphics, model.diagnostic(), pane, y + 3, BONE);
    }

    private void renderRoute(GuiGraphics graphics) {
        Rect pane = layout.route();
        drawPaneLabel(graphics, pane, Component.translatable(layout.paneLabels().routeKey()), BONE);
        int y = pane.y() + (pane.height() < 34 ? 13 : 15);
        y = drawWrapped(graphics, model.questTitle(), pane, y, CYAN);
        y = drawWrapped(graphics, model.questSubtitle(), pane, y + 2, BONE);
        if (model.selectedQuestId().isPresent() && y + 9 < pane.bottom() - 3) {
            drawClippedLine(
                    graphics,
                    Component.literal("Q//" + EchoRoute.formatQuestId(model.selectedQuestId().getAsLong())),
                    pane,
                    y + 3,
                    AMBER);
        }
    }

    private void renderProgress(GuiGraphics graphics) {
        Rect pane = layout.progress();
        drawPaneLabel(graphics, pane, Component.translatable(layout.paneLabels().progressKey()), CYAN);
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
        drawClippedLine(graphics, label, pane, pane.y() + 4, color);
    }

    private void drawClippedLine(GuiGraphics graphics, Component text, Rect pane, int y, int color) {
        if (!layout.canRenderTextLine(pane, y, font.lineHeight)) {
            return;
        }
        Rect clip = layout.textClip(pane);
        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        graphics.drawString(font, text, pane.x() + 5, y, opaque(color), false);
        graphics.disableScissor();
    }

    private int drawWrapped(GuiGraphics graphics, Component text, Rect pane, int y, int color) {
        int textWidth = Math.max(1, pane.width() - 10);
        int currentY = y;
        Rect clip = layout.textClip(pane);
        if (clip.width() == 0 || clip.height() == 0) {
            return currentY;
        }
        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        for (var line : font.split(text, textWidth)) {
            if (!layout.canRenderTextLine(pane, currentY, font.lineHeight)) {
                break;
            }
            graphics.drawString(font, line, pane.x() + 5, currentY, opaque(color), false);
            currentY += font.lineHeight;
        }
        graphics.disableScissor();
        return currentY;
    }

    private void renderMinimalFault(GuiGraphics graphics) {
        Rect faultLine = layout.faultLine();
        if (faultLine.width() == 0 || faultLine.height() == 0) {
            return;
        }
        graphics.enableScissor(faultLine.x(), faultLine.y(), faultLine.right(), faultLine.bottom());
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.afterlight.echo.state.unavailable"),
                layout.logicalWidth() / 2,
                faultLine.y(),
                opaque(FAULT));
        graphics.disableScissor();
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

    private record MutationKey(Action action, long targetId) {
        private MutationKey {
            Objects.requireNonNull(action);
        }
    }

    private record PendingMutation(MutationFingerprint fingerprint, int ticksRemaining) {
        private PendingMutation {
            Objects.requireNonNull(fingerprint);
        }

        private PendingMutation age() {
            return new PendingMutation(fingerprint, ticksRemaining - 1);
        }
    }

    private sealed interface MutationFingerprint permits PinFingerprint, SubmitFingerprint, ClaimFingerprint {
        boolean exists();
    }

    private record PinFingerprint(boolean exists, boolean pinned) implements MutationFingerprint {
    }

    private record SubmitFingerprint(
            boolean exists,
            long currentValue,
            long requiredValue,
            boolean complete,
            boolean supported,
            boolean eligible) implements MutationFingerprint {
        private static final SubmitFingerprint MISSING = new SubmitFingerprint(false, 0L, 0L, false, false, false);
    }

    private record ClaimFingerprint(
            boolean exists,
            boolean claimed,
            boolean supported,
            boolean choice,
            boolean eligible) implements MutationFingerprint {
        private static final ClaimFingerprint MISSING = new ClaimFingerprint(false, false, false, false, false);
    }
}
