package org.rllabs.afterlight.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.rllabs.afterlight.route.EchoRouteLoader.RouteValidationException;

class EchoRouteLoaderTest {
    private final EchoRouteLoader loader = new EchoRouteLoader();

    @Test
    void loadsDefaultPathContract() {
        assertEquals(Path.of("config/afterlight/echo_route.json"), EchoRouteLoader.DEFAULT_PATH);
    }

    @Test
    void loadsReaderAndPreservesConfiguredGraphOrder() throws Exception {
        EchoRoute route = loader.load(new StringReader(validJson()));

        assertEquals(1, route.schema());
        assertEquals(Long.parseUnsignedLong("31C9557D2F51238F", 16), route.terminalQuestId());
        assertEquals(List.of("cold_boot", "memory_trace"), route.segments().stream()
                .map(EchoRoute.Segment::id)
                .toList());
        assertEquals(List.of(), route.segments().get(0).after());
        assertEquals(List.of("cold_boot"), route.segments().get(1).after());
        assertEquals(
                List.of(
                        1L,
                        Long.parseUnsignedLong("8000000000000000", 16),
                        Long.parseUnsignedLong("FFFFFFFFFFFFFFFF", 16),
                        Long.parseUnsignedLong("31C9557D2F51238F", 16)),
                route.questIds());
    }

    @Test
    void loadsPath() throws Exception {
        EchoRoute route = loader.load(resourcePath("valid.json"));

        assertEquals(4, route.questIds().size());
        assertEquals("memory_trace", route.segments().get(1).id());
    }

    @Test
    void parsesAndFormatsUnsignedHexIds() throws Exception {
        EchoRoute route = loader.load(new StringReader(validJson()));

        assertEquals(Long.MIN_VALUE, route.questIds().get(1));
        assertEquals(-1L, route.questIds().get(2));
        assertEquals("0000000000000001", EchoRoute.formatQuestId(route.questIds().get(0)));
        assertEquals("8000000000000000", EchoRoute.formatQuestId(route.questIds().get(1)));
        assertEquals("FFFFFFFFFFFFFFFF", EchoRoute.formatQuestId(route.questIds().get(2)));
    }

    @Test
    void routeCollectionsAreImmutableCopies() throws Exception {
        EchoRoute route = loader.load(new StringReader(validJson()));

        assertThrows(UnsupportedOperationException.class, () -> route.segments().clear());
        assertThrows(UnsupportedOperationException.class, () -> route.questIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> route.segments().get(1).after().clear());
        assertThrows(UnsupportedOperationException.class, () -> route.segments().get(0).quests().clear());
    }

    @Test
    void rejectsUnknownSchema() throws Exception {
        RouteValidationException exception = assertInvalid(resourceText("unknown-state.json"));

        assertEquals(List.of("schema must be 1, found 2"), exception.errors());
    }

    @Test
    void rejectsEmptySegments() {
        RouteValidationException exception = assertInvalid("""
                {"schema":1,"terminal_quest":"01","segments":[]}
                """);

        assertEquals(List.of(
                "segments must contain at least one segment",
                "terminal quest 0000000000000001 is absent from the route"), exception.errors());
    }

    @Test
    void rejectsDuplicateQuestAndSegmentIds() throws Exception {
        RouteValidationException exception = assertInvalid(resourceText("duplicate.json"));

        assertEquals(List.of(
                "duplicate quest ID 0000000000000001 at segments[0].quests[1]",
                "duplicate segment ID cold_boot at segments[1].id"), exception.errors());
    }

    @Test
    void rejectsInvalidTerminalHexId() {
        RouteValidationException exception = assertInvalid("""
                {"schema":1,"terminal_quest":"0x1","segments":[
                  {"id":"root","after":[],"quests":["01"]}
                ]}
                """);

        assertEquals(List.of("terminal_quest must be 1 to 16 hexadecimal digits, found \"0x1\""), exception.errors());
    }

    @Test
    void rejectsInvalidQuestHexIdsTogether() {
        RouteValidationException exception = assertInvalid("""
                {"schema":1,"terminal_quest":"01","segments":[
                  {"id":"root","after":[],"quests":["", "-1", "10000000000000000", "GG"]}
                ]}
                """);

        assertEquals(List.of(
                "segments[0].quests[0] must be 1 to 16 hexadecimal digits, found \"\"",
                "segments[0].quests[1] must be 1 to 16 hexadecimal digits, found \"-1\"",
                "segments[0].quests[2] must be 1 to 16 hexadecimal digits, found \"10000000000000000\"",
                "segments[0].quests[3] must be 1 to 16 hexadecimal digits, found \"GG\"",
                "terminal quest 0000000000000001 is absent from the route"), exception.errors());
    }

    @Test
    void rejectsUnknownSegmentDependencies() {
        RouteValidationException exception = assertInvalid("""
                {"schema":1,"terminal_quest":"02","segments":[
                  {"id":"root","after":[],"quests":["01"]},
                  {"id":"memory","after":["missing"],"quests":["02"]}
                ]}
                """);

        assertEquals(List.of(
                "segment memory has unknown dependency missing",
                "segment memory is unreachable from every zero-dependency root"), exception.errors());
    }

    @Test
    void rejectsCyclesAndUnreachableSegments() throws Exception {
        RouteValidationException exception = assertInvalid(resourceText("cycle.json"));

        assertEquals(List.of(
                "segment dependency cycle: memory_a -> memory_b -> memory_a",
                "segment memory_a is unreachable from every zero-dependency root",
                "segment memory_b is unreachable from every zero-dependency root"), exception.errors());
    }

    @Test
    void rejectsTerminalQuestAbsentFromRoute() {
        RouteValidationException exception = assertInvalid("""
                {"schema":1,"terminal_quest":"FF","segments":[
                  {"id":"root","after":[],"quests":["01"]}
                ]}
                """);

        assertEquals(List.of("terminal quest 00000000000000FF is absent from the route"), exception.errors());
    }

    @Test
    void returnsAllValidationErrorsInDeterministicOrder() {
        String invalid = """
                {"schema":7,"terminal_quest":"FE","segments":[
                  {"id":"alpha","after":["missing"],"quests":["01","GG","01"]},
                  {"id":"alpha","after":[],"quests":["02"]}
                ]}
                """;

        List<String> expected = List.of(
                "schema must be 1, found 7",
                "segments[0].quests[1] must be 1 to 16 hexadecimal digits, found \"GG\"",
                "duplicate quest ID 0000000000000001 at segments[0].quests[2]",
                "duplicate segment ID alpha at segments[1].id",
                "segment alpha has unknown dependency missing",
                "terminal quest 00000000000000FE is absent from the route");

        assertEquals(expected, assertInvalid(invalid).errors());
        assertEquals(expected, assertInvalid(invalid).errors());
    }

    @Test
    void rejectsMissingTopLevelFieldsTogether() {
        RouteValidationException exception = assertInvalid("{}");

        assertEquals(List.of(
                "schema is required",
                "terminal_quest is required",
                "segments is required"), exception.errors());
    }

    @Test
    void rejectsWrongTopLevelFieldTypesTogether() {
        RouteValidationException exception = assertInvalid("""
                {"schema":"1","terminal_quest":1,"segments":{}}
                """);

        assertEquals(List.of(
                "schema must be an integer",
                "terminal_quest must be a string",
                "segments must be an array"), exception.errors());
    }

    @Test
    void rejectsInvalidSegmentShapesTogether() {
        RouteValidationException exception = assertInvalid("""
                {"schema":1,"terminal_quest":"01","segments":[
                  null,
                  {"id":7,"after":"root","quests":{}},
                  {"id":" ","after":[3],"quests":[false]},
                  {}
                ]}
                """);

        assertEquals(List.of(
                "segments[0] must be an object",
                "segments[1].id must be a string",
                "segments[1].after must be an array",
                "segments[1].quests must be an array",
                "segments[2].id must not be blank",
                "segments[2].after[0] must be a string",
                "segments[2].quests[0] must be a string",
                "segments[3].id is required",
                "segments[3].after is required",
                "segments[3].quests is required",
                "terminal quest 0000000000000001 is absent from the route"), exception.errors());
    }

    @Test
    void rejectsMalformedJsonAsValidationFailure() {
        RouteValidationException exception = assertInvalid("{");

        assertEquals(List.of("route JSON is malformed"), exception.errors());
    }

    @Test
    void rejectsNonObjectRoot() {
        RouteValidationException exception = assertInvalid("[]");

        assertEquals(List.of("route root must be an object"), exception.errors());
    }

    private RouteValidationException assertInvalid(String json) {
        return assertThrows(RouteValidationException.class, () -> loader.load(new StringReader(json)));
    }

    private String validJson() throws Exception {
        return resourceText("valid.json");
    }

    private String resourceText(String filename) throws Exception {
        return java.nio.file.Files.readString(resourcePath(filename));
    }

    private Path resourcePath(String filename) throws URISyntaxException {
        var resource = EchoRouteLoaderTest.class.getResource("/routes/" + filename);
        assertTrue(resource != null, "missing test resource " + filename);
        return Path.of(resource.toURI());
    }
}
