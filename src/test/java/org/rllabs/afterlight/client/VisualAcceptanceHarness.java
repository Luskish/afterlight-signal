package org.rllabs.afterlight.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
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
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.opengl.GL11;
import org.rllabs.afterlight.Afterlight;
import org.rllabs.afterlight.integration.EchoQuestGateway;
import org.rllabs.afterlight.route.EchoQuestSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.TaskSnapshot;
import org.rllabs.afterlight.route.EchoRoute;
import org.rllabs.afterlight.visual.VisualRendererPolicy;
import org.rllabs.afterlight.visual.VisualSceneCatalog;
import org.rllabs.afterlight.visual.VisualSceneCatalog.WorldScene;
import org.rllabs.afterlight.visual.VisualSceneReadiness.SceneStability;

@EventBusSubscriber(modid = Afterlight.MOD_ID, value = Dist.CLIENT)
public final class VisualAcceptanceHarness {
    private static final String ENABLE_PROPERTY = "afterlight.visual.acceptance";
    private static final String ROLE_PROPERTY = "afterlight.visual.role";
    private static final String OUTPUT_PROPERTY = "afterlight.visual.output";
    private static final int TIMEOUT_TICKS = 4_800;
    private static final int CAPTURE_TIMEOUT_TICKS = 400;
    private static final int STABLE_SCREEN_TICKS = 3;
    private static final int STABLE_SCENE_TICKS = 12;
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
    private final DeferredFrameCapture deferredFrameCapture = new DeferredFrameCapture();
    private int elapsedTicks;
    private int stepIndex;
    private BooleanSupplier condition;
    private String conditionDescription;
    private int conditionDeadline;
    private PendingCapture pendingCapture;
    private int pendingCaptureStarted;
    private VisualSceneProbe.SceneSnapshot readyScene;
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

    @SubscribeEvent
    public static void onRenderedFrame(RenderFrameEvent.Post event) {
        if (!enabled(System.getProperty(ENABLE_PROPERTY))
                || !"client".equals(System.getProperty(ROLE_PROPERTY))
                || instance == null) {
            return;
        }
        instance.renderedFrame();
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

    static int stableSceneTicks() {
        return STABLE_SCENE_TICKS;
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
                throw failure("screenshot callback timeout for " + pendingCapture.name(), null);
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

    private void renderedFrame() {
        if (finished || asynchronousFailure != null) {
            return;
        }
        try {
            deferredFrameCapture.onRenderedFrame();
        } catch (Throwable throwable) {
            asynchronousFailure = throwable;
        }
    }

    private List<Step> buildSteps() {
        List<Step> planned = new ArrayList<>();
        addScreenCapture(
                planned,
                1920,
                1080,
                2,
                new SignalTitleScreen(),
                "title-1920x1080.png",
                SignalTitleScreen.class);
        addScreenCapture(
                planned,
                3440,
                1440,
                2,
                new SignalTitleScreen(),
                "title-3440x1440.png",
                SignalTitleScreen.class);
        addScreenCapture(
                planned,
                854,
                480,
                2,
                new SignalTitleScreen(),
                "title-854x480.png",
                SignalTitleScreen.class);
        addScreenCapture(
                planned, 1920, 1080, 2, guidedEchoScreen(), "echo-wide.png", EchoScreen.class);
        addScreenCapture(
                planned,
                1280,
                720,
                4,
                guidedEchoScreen(),
                "echo-standard.png",
                EchoScreen.class);
        addScreenCapture(
                planned,
                854,
                480,
                4,
                guidedEchoScreen(),
                "echo-compact.png",
                EchoScreen.class);
        addScreenCapture(
                planned,
                320,
                240,
                4,
                guidedEchoScreen(),
                "echo-minimal.png",
                EchoScreen.class);

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
            await(
                    () -> minecraft.player != null
                            && minecraft.level != null
                            && minecraft.getConnection() != null,
                    "visual server connection",
                    1_200);
        });
        planned.add(() -> {
            minecraft.options.hideGui = false;
            command("item replace entity @s weapon.mainhand with afterlight:echo");
            command("tp @s 64.5 101 12.5 180 8");
        });
        addSceneAwait(planned, "echo-item-gui.png");
        planned.add(() -> minecraft.setScreen(new InventoryScreen(minecraft.player)));
        addScreenAwait(planned, 1920, 1080, InventoryScreen.class);
        planned.add(() -> captureWorldScreen(
                "echo-item-gui.png", InventoryScreen.class, CameraType.FIRST_PERSON));

        planned.add(() -> {
            minecraft.setScreen(null);
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        });
        addSceneAwait(planned, "echo-item-first-person.png");
        planned.add(() -> captureWorld("echo-item-first-person.png", CameraType.FIRST_PERSON));
        planned.add(() -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        addSceneAwait(planned, "echo-item-third-person.png");
        planned.add(() -> captureWorld("echo-item-third-person.png", CameraType.THIRD_PERSON_BACK));
        planned.add(() -> {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            command("tp @s 72.5 101 8.5 180 12");
        });
        addSceneAwait(planned, "echo-item-dropped.png");
        planned.add(() -> captureWorld("echo-item-dropped.png", CameraType.FIRST_PERSON));
        planned.add(() -> command("tp @s 80.5 101 8.5 180 4"));
        addSceneAwait(planned, "echo-item-frame.png");
        planned.add(() -> captureWorld("echo-item-frame.png", CameraType.FIRST_PERSON));

        addWorldCommandCapture(
                planned, "tp @s -24.5 101 14.5 180 -10", "gate-idle.png");
        addWorldCommandCapture(planned, "tp @s 0.5 101 14.5 180 -10", "gate-open.png");
        addWorldCommandCapture(planned, "tp @s 24.5 101 14.5 180 -10", "gate-fault.png");

        planned.add(() -> {
            command("gamemode spectator");
            command("execute in afterlight:far_relay run tp @s 0.5 80 14.5 180 24");
        });
        addSceneAwait(planned, "far-relay-arrival.png");
        planned.add(() -> captureWorld("far-relay-arrival.png", CameraType.FIRST_PERSON));
        addWorldCommandCapture(
                planned,
                "execute in afterlight:far_relay run tp @s 15.5 82 15.5 135 28",
                "far-relay-central.png");
        addWorldCommandCapture(
                planned,
                "execute in afterlight:far_relay run tp @s 241.5 82 0.5 -90 28",
                "far-relay-east.png");
        addWorldCommandCapture(
                planned,
                "execute in afterlight:far_relay run tp @s -240.5 82 0.5 90 28",
                "far-relay-west.png");
        addWorldCommandCapture(
                planned,
                "execute in afterlight:far_relay run tp @s 0.5 82 -240.5 180 28",
                "far-relay-north.png");
        addWorldCommandCapture(
                planned,
                "execute in afterlight:far_relay run tp @s 0.5 82 241.5 0 28",
                "far-relay-south.png");
        addWorldCommandCapture(
                planned,
                "execute in minecraft:overworld run tp @s 0.5 103 14.5 180 -10",
                "far-relay-return.png");
        return List.copyOf(planned);
    }

    private void addScreenCapture(
            List<Step> planned,
            int width,
            int height,
            int guiScale,
            Screen screen,
            String name,
            Class<? extends Screen> expectedScreen) {
        planned.add(() -> {
            resize(width, height, guiScale);
            minecraft.setScreen(screen);
        });
        addScreenAwait(planned, width, height, expectedScreen);
        planned.add(() -> capture(name, expectedScreen, null));
    }

    private void addScreenAwait(
            List<Step> planned,
            int width,
            int height,
            Class<? extends Screen> expectedScreen) {
        planned.add(() -> {
            ScreenPresentationReadiness readiness =
                    new ScreenPresentationReadiness(width, height, STABLE_SCREEN_TICKS);
            await(
                    () -> readiness.update(
                            expectedScreen.isInstance(minecraft.screen),
                            minecraft.getWindow().getWidth(),
                            minecraft.getWindow().getHeight(),
                            minecraft.getOverlay() != null),
                    "screen " + expectedScreen.getName() + " at " + width + "x" + height,
                    400);
        });
    }

    private void addWorldCommandCapture(List<Step> planned, String command, String name) {
        planned.add(() -> this.command(command));
        addSceneAwait(planned, name);
        planned.add(() -> captureWorld(name, CameraType.FIRST_PERSON));
    }

    private void addSceneAwait(List<Step> planned, String artifact) {
        planned.add(() -> awaitScene(VisualSceneCatalog.scene(artifact)));
    }

    private void awaitScene(WorldScene scene) {
        readyScene = null;
        SceneStability stability = new SceneStability(STABLE_SCENE_TICKS);
        await(
                () -> {
                    VisualSceneProbe.SceneSnapshot snapshot = VisualSceneProbe.inspect(minecraft, scene);
                    if (stability.update(snapshot.evaluation())) {
                        readyScene = snapshot;
                        return true;
                    }
                    return false;
                },
                "scene " + scene.artifact(),
                1_200);
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

    private void captureWorld(String name, CameraType expectedCamera) {
        if (minecraft.screen != null) {
            throw new IllegalStateException("Unexpected screen before world capture: "
                    + minecraft.screen.getClass().getName());
        }
        capturePreparedWorld(name, null, expectedCamera);
    }

    private void captureWorldScreen(
            String name,
            Class<? extends Screen> expectedScreen,
            CameraType expectedCamera) {
        capturePreparedWorld(name, expectedScreen, expectedCamera);
    }

    private void capturePreparedWorld(
            String name,
            Class<? extends Screen> expectedScreen,
            CameraType expectedCamera) {
        requireWorld();
        WorldScene scene = VisualSceneCatalog.scene(name);
        VisualSceneProbe.SceneSnapshot current = VisualSceneProbe.inspect(minecraft, scene);
        if (readyScene == null
                || !readyScene.scene().artifact().equals(name)
                || !current.evaluation().ready()) {
            throw new IllegalStateException(
                    "Scene changed before " + name + ": " + current.evaluation().failures());
        }
        if (minecraft.options.getCameraType() != expectedCamera) {
            throw new IllegalStateException("Unexpected camera before " + name);
        }
        readyScene = null;
        capture(name, expectedScreen, current);
    }

    private void capture(
            String name,
            Class<? extends Screen> expectedScreen,
            VisualSceneProbe.SceneSnapshot scene) {
        if (expectedScreen != null && !expectedScreen.isInstance(minecraft.screen)) {
            throw new IllegalStateException("Unexpected screen for " + name + ": "
                    + (minecraft.screen == null ? "none" : minecraft.screen.getClass().getName()));
        }
        Path target = outputRoot.resolve(Screenshot.SCREENSHOT_DIR).resolve(name);
        if (Files.exists(target)) {
            throw new IllegalStateException("Refusing to overwrite visual artifact: " + target);
        }
        PendingCapture requested = new PendingCapture(
                name,
                target,
                scene,
                minecraft.screen == null ? null : minecraft.screen.getClass().getName(),
                minecraft.getWindow().getWidth(),
                minecraft.getWindow().getHeight());
        pendingCapture = requested;
        pendingCaptureStarted = elapsedTicks;
        try {
            deferredFrameCapture.request(() -> captureRenderedFrame(requested));
        } catch (RuntimeException exception) {
            pendingCapture = null;
            throw exception;
        }
    }

    private void captureRenderedFrame(PendingCapture requested) {
        if (pendingCapture != requested) {
            throw new IllegalStateException("Rendered frame arrived without its pending capture");
        }
        String currentScreen = minecraft.screen == null
                ? null
                : minecraft.screen.getClass().getName();
        if (!Objects.equals(requested.screenClass(), currentScreen)) {
            throw new IllegalStateException("Screen changed before rendered frame for "
                    + requested.name()
                    + ": "
                    + (currentScreen == null ? "none" : currentScreen));
        }
        int framebufferWidth = minecraft.getWindow().getWidth();
        int framebufferHeight = minecraft.getWindow().getHeight();
        boolean overlayActive = minecraft.getOverlay() != null;
        if (!ScreenPresentationReadiness.isFrameReady(
                true,
                requested.framebufferWidth(),
                requested.framebufferHeight(),
                framebufferWidth,
                framebufferHeight,
                overlayActive)) {
            if (overlayActive) {
                throw new IllegalStateException(
                        "Overlay became active before rendered frame for " + requested.name());
            }
            throw new IllegalStateException(
                    "Framebuffer dimensions changed before rendered frame for " + requested.name());
        }
        validateRenderedFrame(requested.name());
        Screenshot.grab(
                outputRoot.toFile(),
                requested.name(),
                minecraft.getMainRenderTarget(),
                result -> minecraft.execute(() -> verifyCapture(result.getString())));
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

    private void verifyCapture(String resultMessage) {
        PendingCapture completed = pendingCapture;
        try {
            if (completed == null) {
                throw new IOException("Screenshot callback arrived without a pending capture");
            }
            if (!Files.isRegularFile(completed.target())) {
                throw new IOException("Screenshot API did not create "
                        + completed.target()
                        + ": "
                        + resultMessage);
            }
            BufferedImage image = ImageIO.read(completed.target().toFile());
            if (image == null) {
                throw new IOException("Screenshot is not a PNG: " + completed.target());
            }
            if (image.getWidth() != minecraft.getWindow().getWidth()
                    || image.getHeight() != minecraft.getWindow().getHeight()) {
                throw new IOException("Screenshot dimensions changed for " + completed.name());
            }
            captures.add(new CapturedArtifact(
                    completed.name(),
                    image.getWidth(),
                    image.getHeight(),
                    sha256(Files.readAllBytes(completed.target())),
                    completed.screenClass(),
                    completed.scene()));
            deferredFrameCapture.complete();
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
        GraphicsMetadata graphics = graphicsMetadata();
        JsonArray inventory = new JsonArray();
        for (CapturedArtifact capture : captures) {
            inventory.add(artifactJson(capture));
        }
        JsonObject manifest = new JsonObject();
        manifest.addProperty("schema", 2);
        JsonObject graphicsJson = new JsonObject();
        graphicsJson.addProperty("vendor", graphics.vendor());
        graphicsJson.addProperty("renderer", graphics.renderer());
        graphicsJson.addProperty("version", graphics.version());
        manifest.add("graphics", graphicsJson);
        manifest.add("artifacts", inventory);
        try {
            Files.writeString(outputRoot.resolve("manifest.json"), manifest.toString() + "\n");
            Files.writeString(
                    outputRoot.resolve("visual-acceptance-success.txt"),
                    "VISUAL ACCEPTANCE: OK\n");
        } catch (IOException exception) {
            throw failure("cannot write visual acceptance manifest", exception);
        }
        finished = true;
        System.out.println("VISUAL ACCEPTANCE: OK");
        minecraft.stop();
    }

    private GraphicsMetadata graphicsMetadata() {
        String vendor = GL11.glGetString(GL11.GL_VENDOR);
        String renderer = GL11.glGetString(GL11.GL_RENDERER);
        String version = GL11.glGetString(GL11.GL_VERSION);
        if (!VisualRendererPolicy.isApproved(vendor, renderer, version)) {
            throw failure(
                    "unapproved OpenGL context vendor="
                            + vendor
                            + " renderer="
                            + renderer
                            + " version="
                            + version,
                    null);
        }
        return new GraphicsMetadata(vendor, renderer, version);
    }

    private static JsonObject artifactJson(CapturedArtifact capture) {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", capture.name());
        entry.addProperty("width", capture.width());
        entry.addProperty("height", capture.height());
        entry.addProperty("sha256", capture.sha256());
        if (capture.screenClass() == null) {
            entry.add("screen", JsonNull.INSTANCE);
        } else {
            entry.addProperty("screen", capture.screenClass());
        }
        JsonObject sceneJson = new JsonObject();
        if (capture.scene() == null) {
            sceneJson.add("dimension", JsonNull.INSTANCE);
            sceneJson.add("coordinates", JsonNull.INSTANCE);
            sceneJson.add("relay_platform_y", JsonNull.INSTANCE);
            sceneJson.add("gate_state", JsonNull.INSTANCE);
            sceneJson.add("chunks", new JsonArray());
            JsonArray anchors = new JsonArray();
            JsonObject screenAnchor = new JsonObject();
            screenAnchor.addProperty("name", "screen");
            screenAnchor.addProperty("valid", capture.screenClass() != null);
            anchors.add(screenAnchor);
            sceneJson.add("anchors", anchors);
            sceneJson.addProperty("ready", true);
        } else {
            VisualSceneProbe.SceneSnapshot scene = capture.scene();
            sceneJson.addProperty("dimension", scene.dimension());
            JsonObject coordinates = new JsonObject();
            coordinates.addProperty("x", scene.x());
            coordinates.addProperty("y", scene.y());
            coordinates.addProperty("z", scene.z());
            sceneJson.add("coordinates", coordinates);
            if (scene.relayPlatformY() == null) {
                sceneJson.add("relay_platform_y", JsonNull.INSTANCE);
            } else {
                sceneJson.addProperty("relay_platform_y", scene.relayPlatformY());
            }
            if (scene.gateState() == null) {
                sceneJson.add("gate_state", JsonNull.INSTANCE);
            } else {
                sceneJson.addProperty("gate_state", scene.gateState());
            }
            JsonArray chunks = new JsonArray();
            scene.chunks().forEach(chunk -> {
                JsonObject chunkJson = new JsonObject();
                chunkJson.addProperty("x", chunk.x());
                chunkJson.addProperty("z", chunk.z());
                chunkJson.addProperty("loaded", chunk.loaded());
                chunks.add(chunkJson);
            });
            sceneJson.add("chunks", chunks);
            JsonArray anchors = new JsonArray();
            scene.anchors().forEach(anchor -> {
                JsonObject anchorJson = new JsonObject();
                anchorJson.addProperty("name", anchor.name());
                anchorJson.addProperty("x", anchor.x());
                anchorJson.addProperty("y", anchor.y());
                anchorJson.addProperty("z", anchor.z());
                addNullable(anchorJson, "expected_block", anchor.expectedBlock());
                addNullable(anchorJson, "actual_block", anchor.actualBlock());
                addNullable(anchorJson, "block_entity", anchor.blockEntity());
                addNullable(anchorJson, "gate_state", anchor.gateState());
                anchorJson.addProperty("chunk_loaded", anchor.chunkLoaded());
                anchorJson.addProperty("valid", anchor.valid());
                anchors.add(anchorJson);
            });
            sceneJson.add("anchors", anchors);
            sceneJson.addProperty("ready", scene.evaluation().ready());
            JsonArray failures = new JsonArray();
            scene.evaluation().failures().forEach(failure -> failures.add(failure.name()));
            sceneJson.add("failures", failures);
        }
        entry.add("scene", sceneJson);
        return entry;
    }

    private static void addNullable(JsonObject target, String name, String value) {
        if (value == null) {
            target.add(name, JsonNull.INSTANCE);
        } else {
            target.addProperty(name, value);
        }
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

    private record PendingCapture(
            String name,
            Path target,
            VisualSceneProbe.SceneSnapshot scene,
            String screenClass,
            int framebufferWidth,
            int framebufferHeight) {}

    private record CapturedArtifact(
            String name,
            int width,
            int height,
            String sha256,
            String screenClass,
            VisualSceneProbe.SceneSnapshot scene) {}

    private record GraphicsMetadata(String vendor, String renderer, String version) {}

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
