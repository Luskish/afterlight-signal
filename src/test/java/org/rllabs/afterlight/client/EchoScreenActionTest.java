package org.rllabs.afterlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.rllabs.afterlight.client.EchoScreenModel.Action;
import org.rllabs.afterlight.integration.EchoQuestGateway;
import org.rllabs.afterlight.route.EchoQuestSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.RewardSnapshot;
import org.rllabs.afterlight.route.EchoQuestSnapshot.TaskSnapshot;
import org.rllabs.afterlight.route.EchoRecommendation.Kind;
import org.rllabs.afterlight.route.EchoRoute;

class EchoScreenActionTest {
    private static final long QUEST_ID = 0x11L;
    private static final long OTHER_QUEST_ID = 0x12L;
    private static final long TASK_ID = 0x21L;
    private static final long OTHER_TASK_ID = 0x22L;
    private static final long REWARD_ID = 0x31L;
    private static final long OTHER_REWARD_ID = 0x32L;

    @Test
    void submitDelegatesOnlyToGatewayAndDisablesImmediately() {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, submitSnapshot(0L)));
        EchoScreen screen = new EchoScreen(route(), gateway);

        screen.activate(Action.SUBMIT);
        screen.activate(Action.SUBMIT);

        assertEquals(List.of(TASK_ID), gateway.submissions);
        assertEquals(List.of(), gateway.claims);
        assertEquals(List.of(), gateway.pins);
        assertEquals(List.of(), gateway.archives);
        assertFalse(screen.isActionEnabled(Action.SUBMIT));
    }

    @Test
    void claimDelegatesOnlyToGatewayAndDisablesImmediately() {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, claimSnapshot()));
        EchoScreen screen = new EchoScreen(route(), gateway);

        screen.activate(Action.CLAIM);
        screen.activate(Action.CLAIM);

        assertEquals(List.of(REWARD_ID), gateway.claims);
        assertEquals(List.of(), gateway.submissions);
        assertEquals(List.of(), gateway.pins);
        assertEquals(List.of(), gateway.archives);
        assertFalse(screen.isActionEnabled(Action.CLAIM));
    }

    @Test
    void pinDelegatesExactQuestAndDisablesImmediately() {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, submitSnapshot(0L)));
        EchoScreen screen = new EchoScreen(route(), gateway);

        screen.activate(Action.PIN);
        screen.activate(Action.PIN);

        assertEquals(List.of(QUEST_ID), gateway.pins);
        assertFalse(screen.isActionEnabled(Action.PIN));
    }

    @Test
    void archiveDelegatesExactQuestWithoutMutationCooldown() {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, submitSnapshot(0L)));
        EchoScreen screen = new EchoScreen(route(), gateway);

        screen.activate(Action.ARCHIVE);

        assertEquals(List.of(QUEST_ID), gateway.archives);
        assertTrue(screen.isActionEnabled(Action.ARCHIVE));
    }

    @Test
    void synchronizedStateChangeClearsMutationCooldown() {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, submitSnapshot(0L)));
        EchoScreen screen = new EchoScreen(route(), gateway);
        screen.activate(Action.SUBMIT);
        gateway.snapshots = Map.of(QUEST_ID, submitSnapshot(1L));

        screen.tick();

        assertTrue(screen.isActionEnabled(Action.SUBMIT));
    }

    @Test
    void unchangedStateReleasesMutationAfterBoundedCooldown() {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, submitSnapshot(0L)));
        EchoScreen screen = new EchoScreen(route(), gateway);
        screen.activate(Action.SUBMIT);

        for (int tick = 0; tick < 40; tick++) {
            screen.tick();
        }

        assertTrue(screen.isActionEnabled(Action.SUBMIT));
    }

    @Test
    void pinCooldownIgnoresUnrelatedQuestChangesAndClearsForSelectedQuest() {
        FakeGateway gateway = new FakeGateway(twoQuestSnapshots(submitSnapshot(0L, false), false));
        EchoScreen screen = new EchoScreen(twoQuestRoute(), gateway);

        screen.activate(Action.PIN);
        gateway.snapshots = twoQuestSnapshots(submitSnapshot(0L, false), true);
        screen.tick();
        screen.activate(Action.PIN);

        assertFalse(screen.isActionEnabled(Action.PIN));
        assertEquals(List.of(QUEST_ID), gateway.pins);

        gateway.snapshots = twoQuestSnapshots(submitSnapshot(0L, true), true);
        screen.tick();

        assertTrue(screen.isActionEnabled(Action.PIN));
        screen.activate(Action.PIN);

        assertFalse(screen.isActionEnabled(Action.PIN));
        assertEquals(List.of(QUEST_ID, QUEST_ID), gateway.pins);
    }

    @Test
    void submitCooldownIgnoresUnrelatedQuestChangesAndClearsForExactTaskProgress() {
        FakeGateway gateway = new FakeGateway(twoQuestSnapshots(submitSnapshot(0L), false));
        EchoScreen screen = new EchoScreen(twoQuestRoute(), gateway);

        screen.activate(Action.SUBMIT);
        gateway.snapshots = twoQuestSnapshots(submitSnapshot(0L), true);
        screen.tick();
        screen.activate(Action.SUBMIT);

        assertFalse(screen.isActionEnabled(Action.SUBMIT));
        assertEquals(List.of(TASK_ID), gateway.submissions);

        gateway.snapshots = twoQuestSnapshots(submitSnapshot(1L), true);
        screen.tick();

        assertTrue(screen.isActionEnabled(Action.SUBMIT));
        screen.activate(Action.SUBMIT);

        assertFalse(screen.isActionEnabled(Action.SUBMIT));
        assertEquals(List.of(TASK_ID, TASK_ID), gateway.submissions);
    }

    @Test
    void claimCooldownIgnoresUnrelatedQuestChangesAndClearsForExactRewardState() {
        FakeGateway gateway = new FakeGateway(twoQuestSnapshots(claimSnapshot(false), false));
        EchoScreen screen = new EchoScreen(twoQuestRoute(), gateway);

        screen.activate(Action.CLAIM);
        gateway.snapshots = twoQuestSnapshots(claimSnapshot(false), true);
        screen.tick();
        screen.activate(Action.CLAIM);

        assertFalse(screen.isActionEnabled(Action.CLAIM));
        assertEquals(List.of(REWARD_ID), gateway.claims);

        gateway.snapshots = twoQuestSnapshots(claimSnapshot(true), true);
        screen.tick();

        assertTrue(screen.isActionEnabled(Action.CLAIM));
        screen.activate(Action.CLAIM);

        assertFalse(screen.isActionEnabled(Action.CLAIM));
        assertEquals(List.of(REWARD_ID, OTHER_REWARD_ID), gateway.claims);
    }

    @Test
    void submitDoesNotReplacePendingPinForSameQuest() {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, submitSnapshot(0L)));
        EchoScreen screen = new EchoScreen(route(), gateway);

        screen.activate(Action.PIN);
        screen.activate(Action.SUBMIT);

        assertFalse(screen.isActionEnabled(Action.PIN));
        screen.activate(Action.PIN);
        assertEquals(List.of(QUEST_ID), gateway.pins);
        assertEquals(List.of(TASK_ID), gateway.submissions);
    }

    @Test
    void pinDoesNotReplacePendingSubmitForSameQuest() {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, submitSnapshot(0L)));
        EchoScreen screen = new EchoScreen(route(), gateway);

        screen.activate(Action.SUBMIT);
        screen.activate(Action.PIN);

        assertFalse(screen.isActionEnabled(Action.SUBMIT));
        screen.activate(Action.SUBMIT);
        assertEquals(List.of(TASK_ID), gateway.submissions);
        assertEquals(List.of(QUEST_ID), gateway.pins);
    }

    @Test
    void pinningAnotherSelectedQuestDoesNotReplaceFirstQuestCooldown() {
        FakeGateway gateway = new FakeGateway(twoQuestSnapshots(submitSnapshot(0L), false));
        EchoScreen screen = new EchoScreen(twoQuestRoute(), gateway);

        screen.activate(Action.PIN);
        gateway.snapshots = Map.of(
                QUEST_ID, completeSnapshot(),
                OTHER_QUEST_ID, otherSubmitSnapshot());
        screen.tick();
        screen.activate(Action.PIN);
        gateway.snapshots = Map.of(
                QUEST_ID, submitSnapshot(0L),
                OTHER_QUEST_ID, otherSubmitSnapshot());
        screen.tick();

        assertFalse(screen.isActionEnabled(Action.PIN));
        screen.activate(Action.PIN);
        assertEquals(List.of(QUEST_ID, OTHER_QUEST_ID), gateway.pins);
    }

    @Test
    void transientUnavailableRecoveryPreservesUnchangedPinCooldown() throws Exception {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, submitSnapshot(0L)));
        EchoScreen screen = new EchoScreen(route(), gateway);
        screen.activate(Action.PIN);
        gateway.snapshots = Map.of();

        screen.tick();
        assertUnavailable(screen);
        gateway.snapshots = Map.of(QUEST_ID, submitSnapshot(0L));
        screen.tick();

        assertFalse(screen.isActionEnabled(Action.PIN));
        screen.activate(Action.PIN);
        assertEquals(List.of(QUEST_ID), gateway.pins);
    }

    @Test
    void unavailableCooldownExpiresOnItsTenthTick() throws Exception {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, submitSnapshot(0L)));
        EchoScreen screen = new EchoScreen(route(), gateway);
        screen.activate(Action.PIN);
        gateway.snapshots = Map.of();

        for (int tick = 0; tick < 9; tick++) {
            screen.tick();
        }

        assertEquals(1, pendingMutationCount(screen));
        screen.tick();
        assertEquals(0, pendingMutationCount(screen));

        gateway.snapshots = Map.of(QUEST_ID, submitSnapshot(0L));
        screen.tick();
        assertTrue(screen.isActionEnabled(Action.PIN));
    }

    @Test
    void trustedPinChangeAfterOutageClearsOnlyMatchingPendingMutation() throws Exception {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, submitSnapshot(0L, false)));
        EchoScreen screen = new EchoScreen(route(), gateway);
        screen.activate(Action.PIN);
        screen.activate(Action.SUBMIT);
        gateway.snapshots = Map.of();
        screen.tick();
        assertUnavailable(screen);
        gateway.snapshots = Map.of(QUEST_ID, submitSnapshot(0L, true));

        screen.tick();

        assertTrue(screen.isActionEnabled(Action.PIN));
        assertFalse(screen.isActionEnabled(Action.SUBMIT));
        screen.activate(Action.PIN);
        screen.activate(Action.SUBMIT);
        assertEquals(List.of(QUEST_ID, QUEST_ID), gateway.pins);
        assertEquals(List.of(TASK_ID), gateway.submissions);
    }

    @ParameterizedTest
    @EnumSource(InvalidSnapshots.class)
    void malformedInitialSnapshotsDegradeToUnavailableWithoutMutation(InvalidSnapshots invalid) throws Exception {
        FakeGateway gateway = new FakeGateway(Map.of());
        invalid.configure(gateway);

        EchoScreen screen = assertDoesNotThrow(() -> new EchoScreen(route(), gateway));

        assertUnavailable(screen);
        activateEveryAction(screen);
        assertEquals(0, gateway.mutationCount());
    }

    @ParameterizedTest
    @EnumSource(InvalidSnapshots.class)
    void malformedTickSnapshotsDiscardActionableStateWithoutMutation(InvalidSnapshots invalid) throws Exception {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, submitSnapshot(0L)));
        EchoScreen screen = new EchoScreen(route(), gateway);
        assertTrue(screen.isActionEnabled(Action.SUBMIT));
        invalid.configure(gateway);

        assertDoesNotThrow(screen::tick);

        assertUnavailable(screen);
        activateEveryAction(screen);
        assertEquals(0, gateway.mutationCount());
    }

    @Test
    void partialSnapshotReportsTheActualMissingQuestAndOpensRootArchive() throws Exception {
        FakeGateway gateway = new FakeGateway(Map.of(QUEST_ID, completeSnapshot()));
        EchoScreen screen = new EchoScreen(twoQuestRoute(), gateway);

        EchoScreenModel model = model(screen);
        assertEquals(Kind.SIGNAL_UNAVAILABLE, model.kind());
        assertEquals(OptionalLong.of(OTHER_QUEST_ID), model.selectedQuestId());
        assertEquals(
                Component.translatable(
                        "screen.afterlight.echo.diagnostic.signal_unavailable",
                        EchoRoute.formatQuestId(OTHER_QUEST_ID)),
                model.diagnostic());
        assertTrue(screen.isActionEnabled(Action.ARCHIVE));

        screen.activate(Action.ARCHIVE);

        assertEquals(1, gateway.rootArchives);
        assertEquals(List.of(), gateway.archives);
    }

    @Test
    void malformedRouteKeepsRootArchiveAvailable() {
        FakeGateway gateway = new FakeGateway(Map.of());
        EchoScreen screen = EchoScreen.signalUnavailable(gateway);

        assertTrue(screen.isActionEnabled(Action.ARCHIVE));
        screen.activate(Action.ARCHIVE);

        assertEquals(1, gateway.rootArchives);
        assertEquals(0, gateway.mutationCount());
    }

    private static EchoRoute route() {
        return new EchoRoute(1, QUEST_ID, List.of(new EchoRoute.Segment("root", List.of(), List.of(QUEST_ID))));
    }

    private static EchoRoute twoQuestRoute() {
        return new EchoRoute(
                1,
                OTHER_QUEST_ID,
                List.of(new EchoRoute.Segment("root", List.of(), List.of(QUEST_ID, OTHER_QUEST_ID))));
    }

    private static Map<Long, EchoQuestSnapshot> twoQuestSnapshots(
            EchoQuestSnapshot selected,
            boolean otherPinned) {
        return Map.of(
                QUEST_ID, selected,
                OTHER_QUEST_ID, unrelatedSnapshot(otherPinned));
    }

    private static EchoQuestSnapshot submitSnapshot(long currentValue) {
        return submitSnapshot(currentValue, false);
    }

    private static EchoQuestSnapshot submitSnapshot(long currentValue, boolean pinned) {
        return new EchoQuestSnapshot(
                QUEST_ID,
                "Signal Trace",
                "Recover the missing carrier",
                false,
                true,
                pinned,
                List.of(),
                List.of(new TaskSnapshot(
                        TASK_ID,
                        "Check the relay",
                        currentValue,
                        2L,
                        false,
                        true,
                        true,
                        true)),
                List.of());
    }

    private static EchoQuestSnapshot claimSnapshot() {
        return claimSnapshot(false);
    }

    private static EchoQuestSnapshot claimSnapshot(boolean firstClaimed) {
        return new EchoQuestSnapshot(
                QUEST_ID,
                "Signal Trace",
                "Recover the missing carrier",
                true,
                false,
                false,
                List.of(),
                List.of(),
                List.of(
                        new RewardSnapshot(REWARD_ID, "Recovered signal", firstClaimed, false, true, true),
                        new RewardSnapshot(OTHER_REWARD_ID, "Reserve signal", false, false, true, true)));
    }

    private static EchoQuestSnapshot unrelatedSnapshot(boolean pinned) {
        return new EchoQuestSnapshot(
                OTHER_QUEST_ID,
                "Secondary Trace",
                "Await the primary carrier",
                false,
                false,
                pinned,
                List.of(QUEST_ID),
                List.of(),
                List.of());
    }

    private static EchoQuestSnapshot completeSnapshot() {
        return new EchoQuestSnapshot(
                QUEST_ID,
                "Signal Trace",
                "Recover the missing carrier",
                true,
                false,
                false,
                List.of(),
                List.of(),
                List.of());
    }

    private static EchoQuestSnapshot otherSubmitSnapshot() {
        return new EchoQuestSnapshot(
                OTHER_QUEST_ID,
                "Secondary Trace",
                "Continue through the carrier",
                false,
                true,
                false,
                List.of(),
                List.of(new TaskSnapshot(
                        OTHER_TASK_ID,
                        "Check the secondary relay",
                        0L,
                        2L,
                        false,
                        true,
                        true,
                        true)),
                List.of());
    }

    private static void activateEveryAction(EchoScreen screen) {
        for (Action action : Action.values()) {
            screen.activate(action);
        }
    }

    private static void assertUnavailable(EchoScreen screen) throws Exception {
        EchoScreenModel model = model(screen);

        assertEquals(Kind.SIGNAL_UNAVAILABLE, model.kind());
        assertFalse(screen.isActionEnabled(Action.SUBMIT));
        assertFalse(screen.isActionEnabled(Action.CLAIM));
        assertFalse(screen.isActionEnabled(Action.PIN));
        assertTrue(screen.isActionEnabled(Action.ARCHIVE));
    }

    private static EchoScreenModel model(EchoScreen screen) throws Exception {
        Field modelField = EchoScreen.class.getDeclaredField("model");
        modelField.setAccessible(true);
        return (EchoScreenModel) modelField.get(screen);
    }

    private static int pendingMutationCount(EchoScreen screen) throws Exception {
        for (String fieldName : List.of("pendingMutations", "pendingMutation")) {
            try {
                Field pendingField = EchoScreen.class.getDeclaredField(fieldName);
                pendingField.setAccessible(true);
                Object pending = pendingField.get(screen);
                return pending instanceof Map<?, ?> pendingMap
                        ? pendingMap.size()
                        : pending == null ? 0 : 1;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException("EchoScreen pending mutation state");
    }

    private static final class FakeGateway implements EchoQuestGateway {
        private Map<Long, EchoQuestSnapshot> snapshots;
        private RuntimeException snapshotFailure;
        private final List<Long> submissions = new ArrayList<>();
        private final List<Long> claims = new ArrayList<>();
        private final List<Long> pins = new ArrayList<>();
        private final List<Long> archives = new ArrayList<>();
        private int rootArchives;

        private FakeGateway(Map<Long, EchoQuestSnapshot> snapshots) {
            this.snapshots = new LinkedHashMap<>(snapshots);
        }

        @Override
        public Map<Long, EchoQuestSnapshot> snapshots(EchoRoute route) {
            if (snapshotFailure != null) {
                throw snapshotFailure;
            }
            return snapshots;
        }

        @Override
        public void submit(long taskId) {
            submissions.add(taskId);
        }

        @Override
        public void claim(long rewardId) {
            claims.add(rewardId);
        }

        @Override
        public void togglePin(long questId) {
            pins.add(questId);
        }

        @Override
        public void openArchive(long questId) {
            archives.add(questId);
        }

        public void openArchive() {
            rootArchives++;
        }

        private int mutationCount() {
            return submissions.size() + claims.size() + pins.size() + archives.size();
        }
    }

    private enum InvalidSnapshots {
        NULL_MAP {
            @Override
            void configure(FakeGateway gateway) {
                gateway.snapshots = null;
            }
        },
        NULL_KEY {
            @Override
            void configure(FakeGateway gateway) {
                Map<Long, EchoQuestSnapshot> malformed = new HashMap<>();
                malformed.put(QUEST_ID, submitSnapshot(0L));
                malformed.put(null, unrelatedSnapshot(false));
                gateway.snapshots = malformed;
            }
        },
        NULL_VALUE {
            @Override
            void configure(FakeGateway gateway) {
                Map<Long, EchoQuestSnapshot> malformed = new HashMap<>();
                malformed.put(QUEST_ID, submitSnapshot(0L));
                malformed.put(OTHER_QUEST_ID, null);
                gateway.snapshots = malformed;
            }
        },
        RUNTIME_EXCEPTION {
            @Override
            void configure(FakeGateway gateway) {
                gateway.snapshotFailure = new IllegalStateException("synchronization unavailable");
            }
        };

        abstract void configure(FakeGateway gateway);
    }
}
