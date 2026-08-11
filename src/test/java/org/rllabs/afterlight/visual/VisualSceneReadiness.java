package org.rllabs.afterlight.visual;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class VisualSceneReadiness {
    private VisualSceneReadiness() {}

    public static Evaluation evaluate(ExpectedScene expected, ObservedScene observed) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(observed, "observed");
        EnumSet<Failure> failures = EnumSet.noneOf(Failure.class);
        if (!expected.dimension().equals(observed.dimension())) {
            failures.add(Failure.DIMENSION);
        }
        if (!Double.isFinite(observed.x())
                || !Double.isFinite(observed.y())
                || !Double.isFinite(observed.z())
                || Math.abs(expected.x() - observed.x()) > expected.coordinateTolerance()
                || Math.abs(expected.y() - observed.y()) > expected.coordinateTolerance()
                || Math.abs(expected.z() - observed.z()) > expected.coordinateTolerance()) {
            failures.add(Failure.COORDINATES);
        }
        if (!expected.requiredChunks().stream()
                .allMatch(chunk -> Boolean.TRUE.equals(observed.chunks().get(chunk)))) {
            failures.add(Failure.CHUNKS);
        }
        if (!expected.requiredAnchors().stream()
                .allMatch(anchor -> Boolean.TRUE.equals(observed.anchors().get(anchor)))) {
            failures.add(Failure.ANCHORS);
        }
        if (expected.gateState() != null
                && !expected.gateState().equals(observed.gateState())) {
            failures.add(Failure.GATE_STATE);
        }
        return new Evaluation(failures.isEmpty(), failures);
    }

    public enum Failure {
        DIMENSION,
        COORDINATES,
        CHUNKS,
        ANCHORS,
        GATE_STATE
    }

    public record ExpectedScene(
            String dimension,
            double x,
            double y,
            double z,
            double coordinateTolerance,
            Set<String> requiredChunks,
            Set<String> requiredAnchors,
            String gateState) {
        public ExpectedScene {
            Objects.requireNonNull(dimension, "dimension");
            requiredChunks = Set.copyOf(requiredChunks);
            requiredAnchors = Set.copyOf(requiredAnchors);
            if (coordinateTolerance < 0.0 || !Double.isFinite(coordinateTolerance)) {
                throw new IllegalArgumentException("Coordinate tolerance must be finite and nonnegative");
            }
        }
    }

    public record ObservedScene(
            String dimension,
            double x,
            double y,
            double z,
            Map<String, Boolean> chunks,
            Map<String, Boolean> anchors,
            String gateState) {
        public ObservedScene {
            Objects.requireNonNull(dimension, "dimension");
            chunks = Map.copyOf(chunks);
            anchors = Map.copyOf(anchors);
        }
    }

    public record Evaluation(boolean ready, Set<Failure> failures) {
        public Evaluation {
            failures = Set.copyOf(failures);
            if (ready != failures.isEmpty()) {
                throw new IllegalArgumentException("Ready state must match failures");
            }
        }
    }

    public static final class SceneStability {
        private final int requiredReadySamples;
        private int consecutiveReadySamples;

        public SceneStability(int requiredReadySamples) {
            if (requiredReadySamples < 1) {
                throw new IllegalArgumentException("Required samples must be positive");
            }
            this.requiredReadySamples = requiredReadySamples;
        }

        public boolean update(Evaluation evaluation) {
            if (evaluation.ready()) {
                consecutiveReadySamples++;
            } else {
                consecutiveReadySamples = 0;
            }
            return consecutiveReadySamples >= requiredReadySamples;
        }

        public int consecutiveReadySamples() {
            return consecutiveReadySamples;
        }
    }
}
