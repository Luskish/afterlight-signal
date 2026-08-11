package org.rllabs.afterlight;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ModMetadataTest {
    @Test
    void pinsThePackRuntimeContract() throws Exception {
        var properties = new Properties();
        try (var input = Files.newInputStream(Path.of("gradle.properties"))) {
            properties.load(input);
        }
        assertEquals("1.21.1", properties.getProperty("minecraft_version"));
        assertEquals("21.1.248", properties.getProperty("neo_version"));
        assertEquals("2101.1.30", properties.getProperty("ftb_quests_version"));
        assertEquals("afterlight", properties.getProperty("mod_id"));
    }
}
