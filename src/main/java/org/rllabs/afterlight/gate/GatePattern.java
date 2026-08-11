package org.rllabs.afterlight.gate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Direction;

public final class GatePattern {
    private static final Set<GateLocalPos> SIGNAL_GLASS = Set.of(
            pos(-2, 0),
            pos(2, 0),
            pos(-3, 1),
            pos(3, 1),
            pos(-3, 7),
            pos(3, 7),
            pos(-2, 8),
            pos(2, 8));
    private static final Map<GateLocalPos, GatePart> EXPECTED = createExpected();
    private static final Set<GateLocalPos> INTERIOR = createInterior();

    private GatePattern() {}

    public static Map<GateLocalPos, GatePart> expected(Direction facing) {
        GateLocalPos.requireHorizontal(facing);
        return EXPECTED;
    }

    public static Set<GateLocalPos> interior(Direction facing) {
        GateLocalPos.requireHorizontal(facing);
        return INTERIOR;
    }

    private static Map<GateLocalPos, GatePart> createExpected() {
        Map<GateLocalPos, GatePart> expected = new LinkedHashMap<>();
        for (int v = 0; v <= 8; v++) {
            for (int u = -3; u <= 3; u++) {
                if (u != -3 && u != 3 && v != 0 && v != 8) {
                    continue;
                }
                GateLocalPos position = pos(u, v);
                if (position.equals(pos(0, 0))) {
                    expected.put(position, GatePart.CONTROLLER);
                } else if (SIGNAL_GLASS.contains(position)) {
                    expected.put(position, GatePart.SIGNAL_GLASS);
                } else {
                    expected.put(position, GatePart.FRAME);
                }
            }
        }
        return Collections.unmodifiableMap(expected);
    }

    private static Set<GateLocalPos> createInterior() {
        Set<GateLocalPos> interior = new LinkedHashSet<>();
        for (int v = 1; v <= 7; v++) {
            for (int u = -2; u <= 2; u++) {
                interior.add(pos(u, v));
            }
        }
        return Collections.unmodifiableSet(interior);
    }

    private static GateLocalPos pos(int u, int v) {
        return new GateLocalPos(u, v);
    }

    public enum GatePart {
        FRAME,
        SIGNAL_GLASS,
        CONTROLLER
    }
}
