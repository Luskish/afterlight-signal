package org.rllabs.afterlight.relay;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class FarRelayStructurePlan {
    public static final int PRESENTATION_VERSION = 2;
    private static final Map<RelaySite, Plan> PLANS = buildPlans();

    private FarRelayStructurePlan() {}

    public static Plan forSite(RelaySite site) {
        return PLANS.get(Objects.requireNonNull(site, "site"));
    }

    private static Map<RelaySite, Plan> buildPlans() {
        EnumMap<RelaySite, Plan> plans = new EnumMap<>(RelaySite.class);
        for (RelaySite site : RelaySite.values()) {
            plans.put(site, site == RelaySite.CENTRAL ? central() : satellite(site));
        }
        return Map.copyOf(plans);
    }

    public static BlockPos worldPosition(
            RelaySite site, int platformY, Placement placement) {
        return new BlockPos(
                site.x() + placement.x(),
                platformY + placement.y(),
                site.z() + placement.z());
    }

    public static BlockPos worldPosition(RelaySite site, int platformY, Anchor anchor) {
        return new BlockPos(
                site.x() + anchor.x(),
                platformY + anchor.y(),
                site.z() + anchor.z());
    }

    private static Plan central() {
        Builder builder = new Builder();
        addPlatform(builder, 10);
        builder.place(0, 1, 3, Material.LOOT_CHEST);
        builder.place(3, 1, 0, Material.RETURN_TERMINAL, Direction.WEST, true);
        builder.place(-3, 1, 0, Material.FUTURE_CONSOLE, Direction.EAST, true);

        for (int x : new int[] {-8, 8}) {
            for (int z = -8; z <= 5; z++) {
                builder.place(x, 1, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            }
        }
        for (int x : new int[] {-9, 9}) {
            for (int y = 1; y <= 5; y++) {
                builder.place(x, y, -6, Material.POLISHED_BLACKSTONE_BRICKS);
            }
            builder.place(x, 6, -6, Material.SOUL_LANTERN);
        }

        for (int x : new int[] {-6, -5, 5, 6}) {
            for (int z = -10; z <= -8; z++) {
                int towerHeight = Math.abs(x) == 6 ? 10 : 8;
                for (int y = 1; y <= towerHeight; y++) {
                    builder.place(x, y, z, Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }
        for (int x = -4; x <= 4; x++) {
            builder.place(x, 1, -9, Material.GATE_FRAME);
            builder.place(x, 9, -9, Material.GATE_FRAME);
        }
        for (int y = 2; y <= 8; y++) {
            builder.place(-4, y, -9, Material.GATE_FRAME);
            builder.place(4, y, -9, Material.GATE_FRAME);
        }
        for (int x = -3; x <= 3; x++) {
            for (int y = 2; y <= 8; y++) {
                builder.place(x, y, -9, Material.SIGNAL_GLASS);
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -11; z <= -10; z++) {
                for (int y = 1; y <= 11; y++) {
                    builder.place(x, y, z, Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = 12; y <= 13; y++) {
                builder.place(x, y, -10, Material.GATE_FRAME);
            }
        }
        builder.place(0, 14, -10, Material.SOUL_LANTERN);

        for (int x : new int[] {-6, 6}) {
            for (int z : new int[] {-4, 1}) {
                builder.place(x, 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
                builder.place(x, 2, z, Material.GATE_FRAME);
                builder.place(x, 3, z, Material.SOUL_LANTERN);
            }
        }

        builder.anchor("arrival_floor", 0, 0, 0);
        builder.anchor("return_terminal", 3, 1, 0);
        builder.anchor("future_console", -3, 1, 0);
        builder.anchor("loot_chest", 0, 1, 3);
        builder.anchor("signal_aperture", 0, 6, -9);
        builder.anchor("cathedral_crown", 0, 13, -10);
        builder.anchor("west_buttress", -9, 5, -6);
        builder.anchor("east_buttress", 9, 5, -6);
        return builder.build();
    }

    private static Plan satellite(RelaySite site) {
        Builder builder = new Builder();
        addPlatform(builder, 8);
        Direction facing = site.directionTowardCenter();

        for (int localX = -3; localX <= 3; localX++) {
            for (int localForward = -7; localForward <= -5; localForward++) {
                for (int y = 1; y <= 6; y++) {
                    if (Math.abs(localX) == 3 || localForward == -7 || y <= 2) {
                        builder.placeOriented(
                                localX,
                                y,
                                localForward,
                                Material.POLISHED_BLACKSTONE_BRICKS,
                                facing);
                    }
                }
            }
        }
        for (int y = 2; y <= 7; y++) {
            builder.placeOriented(-2, y, -4, Material.GATE_FRAME, facing);
            builder.placeOriented(2, y, -4, Material.GATE_FRAME, facing);
        }
        for (int y = 3; y <= 6; y++) {
            builder.placeOriented(0, y, -4, Material.SIGNAL_GLASS, facing);
        }
        for (int localX = -2; localX <= 2; localX++) {
            builder.placeOriented(localX, 8, -5, Material.GATE_FRAME, facing);
        }
        builder.placeOriented(0, 9, -5, Material.SOUL_LANTERN, facing);
        builder.placeOriented(0, 1, -3, Material.POLISHED_BLACKSTONE_BRICKS, facing);
        builder.placeOriented(0, 2, -3, Material.FUTURE_CONSOLE, facing, true);

        for (int x : new int[] {-7, 7}) {
            for (int z : new int[] {-7, 7}) {
                for (int y = 1; y <= 4; y++) {
                    builder.place(x, y, z, Material.POLISHED_BLACKSTONE_BRICKS);
                }
                builder.place(x, 5, z, Material.SOUL_LANTERN);
            }
        }
        builder.place(0, 1, 3, Material.LOOT_CHEST);

        int[] console = oriented(0, 2, -3, facing);
        int[] signal = oriented(0, 5, -4, facing);
        int[] crown = oriented(0, 8, -5, facing);
        builder.anchor("arrival_floor", 0, 0, 0);
        builder.anchor("future_console", console[0], console[1], console[2]);
        builder.anchor("loot_chest", 0, 1, 3);
        builder.anchor("signal_slit", signal[0], signal[1], signal[2]);
        builder.anchor("blackbox_crown", crown[0], crown[1], crown[2]);
        return builder.build();
    }

    private static void addPlatform(Builder builder, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) <= radius - 2
                        || Math.abs(x) + Math.abs(z) <= radius * 2 - 3) {
                    builder.place(x, 0, z, Material.RELAY_STONE);
                }
            }
        }
        for (int coordinate = -radius + 2; coordinate <= radius - 2; coordinate++) {
            builder.place(-radius, -1, coordinate, Material.POLISHED_BLACKSTONE_BRICKS);
            builder.place(radius, -1, coordinate, Material.POLISHED_BLACKSTONE_BRICKS);
            builder.place(coordinate, -1, -radius, Material.POLISHED_BLACKSTONE_BRICKS);
            builder.place(coordinate, -1, radius, Material.POLISHED_BLACKSTONE_BRICKS);
        }
    }

    private static int[] oriented(int localX, int y, int localForward, Direction facing) {
        Direction right = facing.getCounterClockWise();
        return new int[] {
            right.getStepX() * localX + facing.getStepX() * localForward,
            y,
            right.getStepZ() * localX + facing.getStepZ() * localForward
        };
    }

    public enum Material {
        RELAY_STONE,
        GATE_FRAME,
        SIGNAL_GLASS,
        RETURN_TERMINAL,
        FUTURE_CONSOLE,
        LOOT_CHEST,
        POLISHED_BLACKSTONE_BRICKS,
        POLISHED_BLACKSTONE_BRICK_WALL,
        SOUL_LANTERN
    }

    public record Placement(
            int x,
            int y,
            int z,
            Material material,
            Direction facing,
            boolean active) {
        public Placement {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(facing, "facing");
            if (!facing.getAxis().isHorizontal()) {
                throw new IllegalArgumentException("Terminal facing must be horizontal");
            }
        }
    }

    public record Anchor(String name, int x, int y, int z) {
        public Anchor {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("Anchor name cannot be blank");
            }
        }
    }

    public static final class Plan {
        private final List<Placement> placements;
        private final List<Anchor> anchors;
        private final Map<Position, Placement> placementsByPosition;
        private final int constructionRadius;
        private final int maximumY;

        private Plan(
                Map<Position, Placement> placements,
                List<Anchor> anchors,
                int constructionRadius,
                int maximumY) {
            this.placements = List.copyOf(placements.values());
            this.anchors = List.copyOf(anchors);
            this.placementsByPosition = Map.copyOf(placements);
            this.constructionRadius = constructionRadius;
            this.maximumY = maximumY;
        }

        public List<Placement> placements() {
            return placements;
        }

        public List<Anchor> anchors() {
            return anchors;
        }

        public int constructionRadius() {
            return constructionRadius;
        }

        public int maximumY() {
            return maximumY;
        }

        public Optional<Placement> placementAt(int x, int y, int z) {
            return Optional.ofNullable(placementsByPosition.get(new Position(x, y, z)));
        }
    }

    private static final class Builder {
        private final Map<Position, Placement> placements = new LinkedHashMap<>();
        private final List<Anchor> anchors = new ArrayList<>();

        private void place(int x, int y, int z, Material material) {
            place(x, y, z, material, Direction.NORTH, false);
        }

        private void place(
                int x,
                int y,
                int z,
                Material material,
                Direction facing,
                boolean active) {
            placements.put(
                    new Position(x, y, z),
                    new Placement(x, y, z, material, facing, active));
        }

        private void placeOriented(
                int localX,
                int y,
                int localForward,
                Material material,
                Direction facing) {
            placeOriented(localX, y, localForward, material, facing, false);
        }

        private void placeOriented(
                int localX,
                int y,
                int localForward,
                Material material,
                Direction facing,
                boolean active) {
            int[] position = oriented(localX, y, localForward, facing);
            place(position[0], position[1], position[2], material, facing, active);
        }

        private void anchor(String name, int x, int y, int z) {
            anchors.add(new Anchor(name, x, y, z));
        }

        private Plan build() {
            for (Anchor anchor : anchors) {
                if (!placements.containsKey(new Position(anchor.x(), anchor.y(), anchor.z()))) {
                    throw new IllegalStateException("Anchor lacks placement: " + anchor);
                }
            }
            int radius = placements.keySet().stream()
                    .mapToInt(position -> Math.max(Math.abs(position.x()), Math.abs(position.z())))
                    .max()
                    .orElse(0);
            int maximumY = placements.keySet().stream()
                    .mapToInt(Position::y)
                    .max()
                    .orElse(0);
            return new Plan(placements, anchors, radius, maximumY);
        }
    }

    private record Position(int x, int y, int z) {}
}
