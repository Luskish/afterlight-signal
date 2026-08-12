package org.rllabs.afterlight.acceptance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

final class DedicatedMigrationAcceptanceMarker {
    private static final String HEADER = "AFTERLIGHT_DEDICATED_MIGRATION_V1";
    private static final String PHASE_KEY = "phase";
    private static final String TOKEN_KEY = "token";

    enum Phase {
        PREPARE("prepare"),
        VERIFY("verify");

        private final String id;

        Phase(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }

        static Phase fromId(String id) {
            for (Phase phase : values()) {
                if (phase.id.equals(id)) {
                    return phase;
                }
            }
            throw new IllegalStateException("Unsupported dedicated migration phase: " + id);
        }
    }

    record Marker(Phase phase, Map<String, String> metadata) {
        Marker {
            phase = Objects.requireNonNull(phase);
            metadata = Map.copyOf(metadata);
        }
    }

    private DedicatedMigrationAcceptanceMarker() {}

    static void write(
            Path marker,
            Phase phase,
            String token,
            Map<String, String> metadata) {
        Objects.requireNonNull(marker);
        Objects.requireNonNull(phase);
        requireToken(token);
        TreeMap<String, String> sortedMetadata = validatedMetadata(metadata);
        StringBuilder payload = new StringBuilder(HEADER)
                .append('\n')
                .append(PHASE_KEY)
                .append('=')
                .append(phase.id())
                .append('\n')
                .append(TOKEN_KEY)
                .append('=')
                .append(token)
                .append('\n');
        sortedMetadata.forEach((key, value) -> payload
                .append(key)
                .append('=')
                .append(value)
                .append('\n'));
        try {
            Path parent = marker.toAbsolutePath().normalize().getParent();
            if (parent == null) {
                throw new IllegalStateException("Dedicated migration marker has no parent");
            }
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, marker.getFileName().toString(), ".tmp");
            try {
                Files.writeString(temporary, payload.toString());
                Files.move(
                        temporary,
                        marker,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Dedicated migration marker could not be written: " + marker,
                    exception);
        }
    }

    static Marker readAndVerify(Path marker, Phase expectedPhase, String expectedToken) {
        Objects.requireNonNull(marker);
        Objects.requireNonNull(expectedPhase);
        requireToken(expectedToken);
        List<String> lines;
        try {
            lines = Files.readAllLines(marker);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Dedicated migration marker could not be read: " + marker,
                    exception);
        }
        if (lines.size() < 3 || !HEADER.equals(lines.getFirst())) {
            throw new IllegalStateException("Dedicated migration marker header is invalid");
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1) {
                throw new IllegalStateException("Dedicated migration marker entry is invalid");
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if (values.putIfAbsent(key, value) != null) {
                throw new IllegalStateException(
                        "Dedicated migration marker key is duplicated: " + key);
            }
        }
        if (!expectedPhase.id().equals(values.remove(PHASE_KEY))) {
            throw new IllegalStateException("Dedicated migration marker phase is invalid");
        }
        if (!expectedToken.equals(values.remove(TOKEN_KEY))) {
            throw new IllegalStateException("Dedicated migration marker token is invalid");
        }
        return new Marker(expectedPhase, values);
    }

    static void requireToken(String token) {
        if (token == null || !token.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException(
                    "Dedicated migration token must be exactly 64 lowercase hexadecimal characters");
        }
    }

    private static TreeMap<String, String> validatedMetadata(Map<String, String> metadata) {
        Objects.requireNonNull(metadata);
        TreeMap<String, String> sorted = new TreeMap<>();
        metadata.forEach((key, value) -> {
            if (key == null
                    || !key.matches("[a-z][a-z0-9_]*")
                    || PHASE_KEY.equals(key)
                    || TOKEN_KEY.equals(key)) {
                throw new IllegalArgumentException(
                        "Dedicated migration metadata key is invalid: " + key);
            }
            if (value == null
                    || value.isBlank()
                    || value.indexOf('\n') >= 0
                    || value.indexOf('\r') >= 0) {
                throw new IllegalArgumentException(
                        "Dedicated migration metadata value is invalid: " + key);
            }
            sorted.put(key, value);
        });
        return sorted;
    }
}
