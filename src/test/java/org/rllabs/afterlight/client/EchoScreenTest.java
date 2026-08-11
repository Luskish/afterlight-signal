package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationThunk;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.rllabs.afterlight.integration.EchoQuestGateway;
import org.rllabs.afterlight.route.EchoQuestSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.TaskSnapshot;
import org.rllabs.afterlight.route.EchoRoute;

class EchoScreenTest {
    private static final long QUEST_ID = 0x11L;

    @Test
    void escapeClosesWithoutChangingProgress() {
        FakeGateway gateway = new FakeGateway();
        TrackingEchoScreen screen = new TrackingEchoScreen(route(), gateway);

        assertTrue(screen.keyPressed(256, 0, 0));

        assertTrue(screen.closed);
        assertEquals(List.of(), gateway.mutations);
    }

    @Test
    void screenDoesNotPauseTheWorld() {
        EchoScreen screen = new EchoScreen(route(), new FakeGateway());

        assertFalse(screen.isPauseScreen());
    }

    @Test
    void narratorReceivesDynamicStateDiagnosticsAndTaskDetail() throws Exception {
        EchoQuestSnapshot snapshot = new EchoQuestSnapshot(
                QUEST_ID,
                "Signal Trace",
                "Recover the missing carrier",
                false,
                true,
                false,
                List.of(),
                List.of(new TaskSnapshot(
                        0x21L,
                        "Check the relay",
                        2L,
                        5L,
                        false,
                        true,
                        true,
                        true)),
                List.of());
        EchoScreen screen = new EchoScreen(route(), new FakeGateway(Map.of(QUEST_ID, snapshot)));
        NarrationCapture narration = new NarrationCapture();
        Method method = Screen.class.getDeclaredMethod("updateNarrationState", NarrationElementOutput.class);
        method.setAccessible(true);

        method.invoke(screen, narration);

        String hints = narration.joined(NarratedElementType.HINT);
        assertTrue(hints.contains("screen.afterlight.echo.state.actionable"));
        assertTrue(hints.contains("screen.afterlight.echo.diagnostic.submit"));
        assertTrue(hints.contains("ACT // ROOT"));
        assertTrue(hints.contains("MEMORY // 0"));
        assertTrue(hints.contains("Signal Trace"));
        assertTrue(hints.contains("PREREQUISITES // CLEAR"));
        assertTrue(hints.contains("TASK // Check the relay // 2 / 5 // INCOMPLETE"));
        assertTrue(hints.contains("TASK VALUE // 2 / 5"));
        assertTrue(hints.contains("TASK INCOMPLETE"));
    }

    @Test
    void visibleSectionsContainEveryRequiredDynamicLine() throws Exception {
        EchoQuestSnapshot snapshot = new EchoQuestSnapshot(
                QUEST_ID,
                "Signal Trace",
                "Recover the missing carrier",
                false,
                true,
                false,
                List.of(),
                List.of(new TaskSnapshot(
                        0x21L,
                        "Check the relay",
                        2L,
                        5L,
                        false,
                        true,
                        true,
                        true)),
                List.of());
        EchoScreen screen = new EchoScreen(route(), new FakeGateway(Map.of(QUEST_ID, snapshot)));
        EchoScreenModel model = model(screen);

        assertEquals(
                List.of("screen.afterlight.echo.state.actionable", "ACT // ROOT", "MEMORY // 0"),
                visibleLines("headerDetails", model, false));
        assertEquals(
                List.of(
                        "Signal Trace",
                        "Recover the missing carrier",
                        "PREREQUISITES // CLEAR",
                        "TASK // Check the relay // 2 / 5 // INCOMPLETE",
                        "Q//0000000000000011"),
                visibleLines("routeLines", model));
        assertTrue(visibleLines("progressLines", model).containsAll(List.of(
                "Check the relay",
                "TASK VALUE // 2 / 5",
                "TASK INCOMPLETE")));
    }

    @Test
    void mouseWheelMovesTheProductionRouteAndProgressViewports() throws Exception {
        EchoScreen screen = new EchoScreen(route(), new FakeGateway());
        EchoScreenLayout layout = EchoScreenLayout.compute(854, 480, 4);
        setField(screen, "layout", layout);

        Object routeScroller = field(screen, "routeScroller");
        Object progressScroller = field(screen, "progressScroller");
        primeScroller(routeScroller);
        primeScroller(progressScroller);

        assertTrue(screen.mouseScrolled(
                layout.route().x() + 1.0D,
                layout.route().y() + 1.0D,
                0.0D,
                -1.0D));
        assertEquals(1, scrollerOffset(routeScroller));
        assertEquals(0, scrollerOffset(progressScroller));

        assertTrue(screen.mouseScrolled(
                layout.progress().x() + 1.0D,
                layout.progress().y() + 1.0D,
                0.0D,
                -1.0D));
        assertEquals(1, scrollerOffset(routeScroller));
        assertEquals(1, scrollerOffset(progressScroller));
    }

    @Test
    void renderPathUsesMeasuredCapacityAndScrollerWindows() throws Exception {
        String paneDescriptor = Type.getMethodDescriptor(
                Type.VOID_TYPE,
                Type.getType(net.minecraft.client.gui.GuiGraphics.class));
        List<String> routeCalls = methodCalls("renderRoute", paneDescriptor);
        List<String> progressCalls = methodCalls("renderProgress", paneDescriptor);
        String viewportDescriptor = Type.getMethodDescriptor(
                Type.VOID_TYPE,
                Type.getType(net.minecraft.client.gui.GuiGraphics.class),
                Type.getType(EchoScreenLayout.Rect.class),
                Type.getType(List.class),
                Type.getType(EchoPaneScroller.class),
                Type.BOOLEAN_TYPE,
                Type.INT_TYPE);
        List<String> viewportCalls = methodCalls("renderScrollableLines", viewportDescriptor);

        assertTrue(routeCalls.contains("org/rllabs/afterlight/client/EchoScreen#wrapDetailLines"));
        assertTrue(routeCalls.contains("org/rllabs/afterlight/client/EchoScreen#renderScrollableLines"));
        assertTrue(progressCalls.contains("org/rllabs/afterlight/client/EchoScreen#wrapDetailLines"));
        assertTrue(progressCalls.contains("org/rllabs/afterlight/client/EchoScreen#renderScrollableLines"));
        assertTrue(viewportCalls.contains("org/rllabs/afterlight/client/EchoScreenLayout#detailLineCapacity"));
        assertTrue(viewportCalls.contains("org/rllabs/afterlight/client/EchoPaneScroller#window"));
    }

    private static EchoScreenModel model(EchoScreen screen) throws Exception {
        var field = EchoScreen.class.getDeclaredField("model");
        field.setAccessible(true);
        return (EchoScreenModel) field.get(screen);
    }

    private static Object field(EchoScreen screen, String name) throws Exception {
        var field = EchoScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    private static void setField(EchoScreen screen, String name, Object value) throws Exception {
        var field = EchoScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(screen, value);
    }

    private static void primeScroller(Object scroller) throws Exception {
        Method window = scroller.getClass().getDeclaredMethod("window", List.class, int.class);
        window.setAccessible(true);
        window.invoke(scroller, List.of("one", "two", "three"), 1);
    }

    private static int scrollerOffset(Object scroller) throws Exception {
        Method offset = scroller.getClass().getDeclaredMethod("offset");
        offset.setAccessible(true);
        return (int) offset.invoke(scroller);
    }

    private static List<String> methodCalls(String methodName, String descriptor) throws Exception {
        String resourceName = "/" + EchoScreen.class.getName().replace('.', '/') + ".class";
        try (InputStream input = EchoScreen.class.getResourceAsStream(resourceName)) {
            List<String> calls = new ArrayList<>();
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String methodDescriptor,
                        String signature,
                        String[] exceptions) {
                    if (!methodName.equals(name) || !descriptor.equals(methodDescriptor)) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String name,
                                String invokedDescriptor,
                                boolean isInterface) {
                            calls.add(owner + "#" + name);
                        }
                    };
                }
            }, 0);
            return List.copyOf(calls);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> visibleLines(String methodName, EchoScreenModel model, Object... extraArguments)
            throws Exception {
        Class<?>[] parameterTypes = new Class<?>[extraArguments.length + 1];
        Object[] arguments = new Object[extraArguments.length + 1];
        parameterTypes[0] = EchoScreenModel.class;
        arguments[0] = model;
        for (int index = 0; index < extraArguments.length; index++) {
            Object argument = extraArguments[index];
            parameterTypes[index + 1] = argument instanceof Boolean ? boolean.class : argument.getClass();
            arguments[index + 1] = argument;
        }
        Method method = EchoScreen.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return ((List<net.minecraft.network.chat.Component>) method.invoke(null, arguments)).stream()
                .map(net.minecraft.network.chat.Component::getString)
                .toList();
    }

    private static EchoRoute route() {
        return new EchoRoute(1, QUEST_ID, List.of(new EchoRoute.Segment("root", List.of(), List.of(QUEST_ID))));
    }

    private static final class TrackingEchoScreen extends EchoScreen {
        private boolean closed;

        private TrackingEchoScreen(EchoRoute route, EchoQuestGateway gateway) {
            super(route, gateway);
        }

        @Override
        public void onClose() {
            closed = true;
        }
    }

    private static final class FakeGateway implements EchoQuestGateway {
        private final List<String> mutations = new ArrayList<>();
        private final Map<Long, EchoQuestSnapshot> snapshots;

        private FakeGateway() {
            this(Map.of());
        }

        private FakeGateway(Map<Long, EchoQuestSnapshot> snapshots) {
            this.snapshots = Map.copyOf(snapshots);
        }

        @Override
        public Map<Long, EchoQuestSnapshot> snapshots(EchoRoute route) {
            return snapshots;
        }

        @Override
        public void submit(long taskId) {
            mutations.add("submit");
        }

        @Override
        public void claim(long rewardId) {
            mutations.add("claim");
        }

        @Override
        public void togglePin(long questId) {
            mutations.add("pin");
        }

        @Override
        public void openArchive() {
            mutations.add("archive-root");
        }

        @Override
        public void openArchive(long questId) {
            mutations.add("archive");
        }
    }

    private static final class NarrationCapture implements NarrationElementOutput {
        private final Map<NarratedElementType, List<String>> entries = new EnumMap<>(NarratedElementType.class);

        @Override
        public void add(NarratedElementType type, NarrationThunk<?> thunk) {
            thunk.getText(text -> entries.computeIfAbsent(type, ignored -> new ArrayList<>()).add(text));
        }

        @Override
        public NarrationElementOutput nest() {
            return this;
        }

        private String joined(NarratedElementType type) {
            return String.join(" ", entries.getOrDefault(type, List.of()));
        }
    }
}
