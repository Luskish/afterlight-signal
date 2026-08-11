package org.rllabs.afterlight.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.rllabs.afterlight.Afterlight;
import org.rllabs.afterlight.integration.EchoQuestGateway;
import org.rllabs.afterlight.relay.FarRelayKeys;
import org.rllabs.afterlight.route.EchoQuestSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.TaskSnapshot;
import org.rllabs.afterlight.route.EchoRoute;

@EventBusSubscriber(modid = Afterlight.MOD_ID, value = Dist.CLIENT)
public final class VisualAcceptanceHarness {
    private static final String ENABLE_PROPERTY = "afterlight.visual.acceptance";
    private static final String ROLE_PROPERTY = "afterlight.visual.role";
    private static final String OUTPUT_PROPERTY = "afterlight.visual.output";
    private static final int TIMEOUT_TICKS = 4_800;
    private static final int CAPTURE_TIMEOUT_TICKS = 400;
    private static final List<String> EXPECTED_ARTIFACTS = List.of(
            "title-1920x1080.png",
            "title-3440x1440.png",
            "title-854x480.png",
            "echo-wide.png",
            "echo-standard.png",
            "echo-compact.png",
            "echo-minimal.png",
            "echo-item-gui.png",
            "echo-item-first-person.png",
            "echo-item-third-person.png",
            "echo-item-dropped.png",
            "echo-item-frame.png",
            "gate-idle.png",
            "gate-open.png",
            "gate-fault.png",
            "far-relay-arrival.png",
            "far-relay-central.png",
            "far-relay-east.png",
            "far-relay-west.png",
            "far-relay-north.png",
            "far-relay-south.png",
            "far-relay-return.png");

    private static VisualAcceptanceHarness instance;

    private final Minecraft minecraft;
    private final Path outputRoot;
    private final List<Step> steps;
    private final List<CapturedArtifact> captures = new ArrayList<>();
    private int elapsedTicks;
    private int stepIndex;
    private int waitTicks;
    private BooleanSupplier condition;
    private String conditionDescription;
    private int conditionDeadline;
    private String pendingCapture;
    private int pendingCaptureStarted;
    private boolean finished;
    private volatile Throwable asynchronousFailure;

    private VisualAcceptanceHarness(Minecraft minecraft) {
        this.minecraft = minecraft;
        String output = System.getProperty(OUTPUT_PROPERTY);
        if (output == null || output.isBlank()) {
            throw new IllegalStateException("Missing visual output property: " + OUTPUT_PROPERTY);
        }
        outputRoot = Path.of(output).toAbsolutePath().normalize();
        try {
            Files.createDirectories(outputRoot.resolve(Screenshot.SCREENSHOT_DIR));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create visual output directory", exception);
        }
        steps = buildSteps();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!enabled(System.getProperty(ENABLE_PROPERTY))
                || !"client".equals(System.getProperty(ROLE_PROPERTY))) {
            return;
        }
        if (instance == null) {
            instance = new VisualAcceptanceHarness(Minecraft.getInstance());
        }
        instance.tick();
    }

    static boolean enabled(String value) {
        return "true".equals(value);
    }

    static List<String> expectedArtifacts() {
        return EXPECTED_ARTIFACTS;
    }

    static int timeoutTicks() {
        return TIMEOUT_TICKS;
    }

    private void tick() {
        if (finished) {
            return;
        }
        elapsedTicks++;
        if (asynchronousFailure != null) {
            throw failure("asynchronous screenshot failure", asynchronousFailure);
        }
        if (elapsedTicks > TIMEOUT_TICKS) {
            throw failure("global timeout at step " + stepIndex, null);
        }
        if (pendingCapture != null) {
            if (elapsedTicks - pendingCaptureStarted > CAPTURE_TIMEOUT_TICKS) {
                throw failure("screenshot callback timeout for " + pendingCapture, null);
            }
            return;
        }
        if (condition != null) {
            if (condition.getAsBoolean()) {
                condition = null;
                conditionDescription = null;
            } else if (elapsedTicks > conditionDeadline) {
                throw failure("condition timeout: " + conditionDescription, null);
            } else {
                return;
            }
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (stepIndex >= steps.size()) {
            finish();
            return;
        }
        Step step = steps.get(stepIndex++);
        try {
            step.run();
        } catch (RuntimeException exception) {
            throw failure("step " + (stepIndex - 1) + " failed", exception);
        }
    }

    private List<Step> buildSteps() {
        List<Step> planned = new ArrayList<>();
        addScreenCapture(planned, 1920, 1080, 2, new SignalTitleScreen(),
                "title-1920x1080.png", SignalTitleScreen.class);
        addScreenCapture(planned, 3440, 1440, 2, new SignalTitleScreen(),
                "title-3440x1440.png", SignalTitleScreen.class);
        addScreenCapture(planned, 854, 480, 2, new SignalTitleScreen(),
                "title-854x480.png", SignalTitleScreen.class);
        addScreenCapture(planned, 1920, 1080, 2, guidedEchoScreen(),
                "echo-wide.png", EchoScreen.class);
        addScreenCapture(planned, 1280, 720, 4, guidedEchoScreen(),
                "echo-standard.png", EchoScreen.class);
        addScreenCapture(planned, 854, 480, 4, guidedEchoScreen(),
                "echo-compact.png", EchoScreen.class);
        addScreenCapture(planned, 320, 240, 4, guidedEchoScreen(),
                "echo-minimal.png", EchoScreen.class);

        planned.add(() -> {
            resize(1920, 1080, 2);
            ServerData server = new ServerData(
                    "AFTERLIGHT Visual Acceptance", "127.0.0.1:25567", ServerData.Type.OTHER);
            ConnectScreen.startConnecting(
                    new SignalTitleScreen(),
                    minecraft,
                    new ServerAddress("127.0.0.1", 25567),
                    server,
                    false,
                    null);
            await(() -> minecraft.player != null && minecraft.level != null,
                    "visual server connection", 1_200);
        });
        planned.add(() -> {
            minecraft.options.hideGui = false;
            command("gamemode creative");
            command("item replace entity @s weapon.mainhand with afterlight:echo");
            command("tp @s 64.5 103 12.5 180 8");
            waitTicks = 40;
        });
        planned.add(() -> {
            requireWorld();
            minecraft.setScreen(new InventoryScreen(minecraft.player));
            waitTicks = 12;
        });
        planned.add(() -> capture("echo-item-gui.png", InventoryScreen.class));
        planned.add(() -> {
            minecraft.setScreen(null);
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            waitTicks = 16;
        });
        planned.add(() -> captureWorld("echo-item-first-person.png", false));
        planned.add(() -> {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            waitTicks = 16;
        });
        planned.add(() -> captureWorld("echo-item-third-person.png", false));
        planned.add(() -> {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            command("tp @s 72.5 103 8.5 180 12");
            waitTicks = 30;
        });
        planned.add(() -> captureWorld("echo-item-dropped.png", false));
        planned.add(() -> {
            command("tp @s 80.5 103 8.5 180 4");
            waitTicks = 30;
        });
        planned.add(() -> captureWorld("echo-item-frame.png", false));

        addWorldCommandCapture(planned, "tp @s -24.5 103 14.5 180 -10", "gate-idle.png", false);
        addWorldCommandCapture(planned, "tp @s 0.5 103 14.5 180 -10", "gate-open.png", false);
        addWorldCommandCapture(planned, "tp @s 24.5 103 14.5 180 -10", "gate-fault.png", false);

        planned.add(() -> {
            command("gamemode spectator");
            command("execute in afterlight:far_relay run tp @s 0.5 80 14.5 180 24");
            await(this::inFarRelay, "Far Relay arrival", 1_200);
            waitTicks = 60;
        });
        planned.add(() -> captureWorld("far-relay-arrival.png", true));
        addWorldCommandCapture(
                planned,
                "execute in afterlight:far_relay run tp @s 15.5 82 15.5 135 28",
                "far-relay-central.png",
                true);
        addWorldCommandCapture(
                planned,
                "execute in afterlight:far_relay run tp @s 256.5 82 14.5 180 28",
                "far-relay-east.png",
                true);
        addWorldCommandCapture(
                planned,
                "execute in afterlight:far_relay run tp @s -256.5 82 14.5 180 28",
                "far-relay-west.png",
                true);
        addWorldCommandCapture(
                planned,
                "execute in afterlight:far_relay run tp @s 0.5 82 -241.5 180 28",
                "far-relay-north.png",
                true);
        addWorldCommandCapture(
                planned,
                "execute in afterlight:far_relay run tp @s 0.5 82 270.5 180 28",
                "far-relay-south.png",
                true);
        addWorldCommandCapture(
                planned,
                "execute in minecraft:overworld run tp @s 0.5 103 14.5 180 -10",
                "far-relay-return.png",
                false);
        return List.copyOf(planned);
    }

    private void addScreenCapture(
            List<Step> planned,
            int width,
            int height,
            int guiScale,
            net.minecraft.client.gui.screens.Screen screen,
            String name,
            Class<? extends net.minecraft.client.gui.screens.Screen> expectedScreen) {
        planned.add(() -> {
            resize(width, height, guiScale);
            minecraft.setScreen(screen);
            waitTicks = 16;
        });
        planned.add(() -> capture(name, expectedScreen));
    }

    private void addWorldCommandCapture(
            List<Step> planned, String command, String name, boolean farRelay) {
        planned.add(() -> {
            this.command(command);
            waitTicks = 50;
        });
        planned.add(() -> captureWorld(name, farRelay));
    }

    private void resize(int width, int height, int guiScale) {
        minecraft.options.guiScale().set(guiScale);
        minecraft.getWindow().setWindowed(width, height);
        minecraft.resizeDisplay();
    }

    private void command(String command) {
        requireWorld();
        minecraft.getConnection().sendCommand(command);
    }

    private void requireWorld() {
        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null) {
            throw new IllegalStateException("Expected connected production world");
        }
    }

    private void await(BooleanSupplier awaitedCondition, String description, int additionalTicks) {
        condition = awaitedCondition;
        conditionDescription = description;
        conditionDeadline = elapsedTicks + additionalTicks;
    }

    private boolean inFarRelay() {
        return minecraft.level != null && minecraft.level.dimension().equals(FarRelayKeys.LEVEL);
    }

    private void captureWorld(String name, boolean farRelay) {
        requireWorld();
        if (minecraft.screen != null) {
            throw new IllegalStateException("Unexpected screen before world capture: "
                    + minecraft.screen.getClass().getName());
        }
        if (inFarRelay() != farRelay) {
            throw new IllegalStateException("Unexpected dimension before " + name);
        }
        capture(name, null);
    }

    private void capture(
            String name, Class<? extends net.minecraft.client.gui.screens.Screen> expectedScreen) {
        if (expectedScreen != null && !expectedScreen.isInstance(minecraft.screen)) {
            throw new IllegalStateException("Unexpected screen for " + name + ": "
                    + (minecraft.screen == null ? "none" : minecraft.screen.getClass().getName()));
        }
        Path target = outputRoot.resolve(Screenshot.SCREENSHOT_DIR).resolve(name);
        if (Files.exists(target)) {
            throw new IllegalStateException("Refusing to overwrite visual artifact: " + target);
        }
        validateRenderedFrame(name);
        pendingCapture = name;
        pendingCaptureStarted = elapsedTicks;
        Screenshot.grab(
                outputRoot.toFile(),
                name,
                minecraft.getMainRenderTarget(),
                result -> minecraft.execute(() -> verifyCapture(name, target, result.getString())));
    }

    private void validateRenderedFrame(String name) {
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            if (image.getWidth() != minecraft.getWindow().getWidth()
                    || image.getHeight() != minecraft.getWindow().getHeight()) {
                throw new IllegalStateException("Render target dimensions changed for " + name);
            }
            Set<Integer> sampledColors = new HashSet<>();
            int horizontalStep = Math.max(1, image.getWidth() / 32);
            int verticalStep = Math.max(1, image.getHeight() / 18);
            for (int y = 0; y < image.getHeight(); y += verticalStep) {
                for (int x = 0; x < image.getWidth(); x += horizontalStep) {
                    sampledColors.add(image.getPixelRGBA(x, y));
                }
            }
            if (sampledColors.size() < 8) {
                throw new IllegalStateException("Missing or blank rendered frame for " + name);
            }
        }
    }

    private void verifyCapture(String name, Path target, String resultMessage) {
        try {
            if (!Files.isRegularFile(target)) {
                throw new IOException("Screenshot API did not create " + target + ": " + resultMessage);
            }
            BufferedImage image = ImageIO.read(target.toFile());
            if (image == null) {
                throw new IOException("Screenshot is not a PNG: " + target);
            }
            if (image.getWidth() != minecraft.getWindow().getWidth()
                    || image.getHeight() != minecraft.getWindow().getHeight()) {
                throw new IOException("Screenshot dimensions changed for " + name);
            }
            captures.add(new CapturedArtifact(
                    name,
                    image.getWidth(),
                    image.getHeight(),
                    sha256(Files.readAllBytes(target))));
            pendingCapture = null;
        } catch (Throwable throwable) {
            asynchronousFailure = throwable;
        }
    }

    private void finish() {
        List<String> actual = captures.stream().map(CapturedArtifact::name).toList();
        if (!actual.equals(EXPECTED_ARTIFACTS)) {
            throw failure("artifact inventory mismatch: " + actual, null);
        }
        JsonArray inventory = new JsonArray();
        for (CapturedArtifact capture : captures) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", capture.name());
            entry.addProperty("width", capture.width());
            entry.addProperty("height", capture.height());
            entry.addProperty("sha256", capture.sha256());
            inventory.add(entry);
        }
        JsonObject manifest = new JsonObject();
        manifest.addProperty("schema", 1);
        manifest.addProperty("renderer", "Minecraft Screenshot API under Xvfb and Mesa");
        manifest.add("artifacts", inventory);
        try {
            Files.writeString(outputRoot.resolve("manifest.json"), manifest.toString() + "\n");
            Files.writeString(outputRoot.resolve("visual-acceptance-success.txt"),
                    "VISUAL ACCEPTANCE: OK\n");
        } catch (IOException exception) {
            throw failure("cannot write visual acceptance manifest", exception);
        }
        finished = true;
        System.out.println("VISUAL ACCEPTANCE: OK");
        minecraft.stop();
    }

    private static EchoScreen guidedEchoScreen() {
        long questId = 0x11L;
        EchoRoute route = new EchoRoute(
                1,
                questId,
                List.of(new EchoRoute.Segment("recovered_signal", List.of(), List.of(questId))));
        EchoQuestSnapshot snapshot = new EchoQuestSnapshot(
                questId,
                "Signal Reliquary Calibration",
                "Align the recovered carrier with the Far Relay.",
                false,
                true,
                false,
                List.of(),
                List.of(new TaskSnapshot(
                        0x21L,
                        "Stabilize the cyan carrier",
                        7L,
                        11L,
                        false,
                        true,
                        true,
                        true)),
                List.of());
        return new EchoScreen(route, new FixtureGateway(Map.of(questId, snapshot)));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static IllegalStateException failure(String message, Throwable cause) {
        return new IllegalStateException("AFTERLIGHT visual acceptance failed: " + message, cause);
    }

    @FunctionalInterface
    private interface Step {
        void run();
    }

    private record CapturedArtifact(String name, int width, int height, String sha256) {}

    private record FixtureGateway(Map<Long, EchoQuestSnapshot> snapshots)
            implements EchoQuestGateway {
        private FixtureGateway {
            snapshots = Map.copyOf(snapshots);
        }

        @Override
        public Map<Long, EchoQuestSnapshot> snapshots(EchoRoute route) {
            return snapshots;
        }

        @Override
        public void submit(long taskId) {}

        @Override
        public void claim(long rewardId) {}

        @Override
        public void togglePin(long questId) {}

        @Override
        public void openArchive() {}

        @Override
        public void openArchive(long questId) {}
    }
}
