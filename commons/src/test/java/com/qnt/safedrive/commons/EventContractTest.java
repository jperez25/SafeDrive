package com.qnt.safedrive.commons;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Checks the event contracts in docs/contracts/events: every example validates against its schema,
 * broken events are rejected, and the commons envelope serializes to a valid envelope.
 */
class EventContractTest {

    private static final Path EVENTS = Path.of("..", "docs", "contracts", "events");
    private static final String ID_PREFIX = "https://contracts.safedrive.local/events/";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDialect(
            Dialects.getDraft202012(), builder -> builder.schemas(EventContractTest::readSchema));

    private static String readSchema(String id) {
        if (!id.startsWith(ID_PREFIX)) {
            return null;
        }
        Path file = EVENTS.resolve(id.substring(ID_PREFIX.length()));
        return Files.exists(file) ? read(file) : null;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static Schema schema(String name) {
        return REGISTRY.getSchema(SchemaLocation.of(ID_PREFIX + name + ".v1.schema.json"));
    }

    private static ObjectNode example(String name) {
        return (ObjectNode) MAPPER.readTree(read(EVENTS.resolve("examples").resolve(name + ".v1.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"trip.raw", "trip.completed", "driving.event", "score.updated", "premium.adjusted"})
    void exampleMatchesSchema(String type) {
        List<Error> errors = schema(type).validate(example(type));
        assertThat(errors).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"trip.raw", "trip.completed", "driving.event", "score.updated", "premium.adjusted"})
    void missingCorrelationIdIsRejected(String type) {
        ObjectNode event = example(type);
        event.remove("correlationId");
        assertThat(schema(type).validate(event)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"trip.raw", "trip.completed", "driving.event", "score.updated", "premium.adjusted"})
    void wrongTypeIsRejected(String type) {
        ObjectNode event = example(type);
        event.put("type", "something.else");
        assertThat(schema(type).validate(event)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"trip.raw", "trip.completed", "driving.event", "score.updated", "premium.adjusted"})
    void unknownEnvelopeFieldIsRejected(String type) {
        ObjectNode event = example(type);
        event.put("surprise", true);
        assertThat(schema(type).validate(event)).isNotEmpty();
    }

    @Test
    void unknownPayloadFieldIsRejected() {
        ObjectNode event = example("score.updated");
        ((ObjectNode) event.get("payload")).put("surprise", 1);
        assertThat(schema("score.updated").validate(event)).isNotEmpty();
    }

    @Test
    void outOfRangeScoreIsRejected() {
        ObjectNode event = example("score.updated");
        ((ObjectNode) event.get("payload")).put("score", 101);
        assertThat(schema("score.updated").validate(event)).isNotEmpty();
    }

    @Test
    void commonsEnvelopeSerializesToValidEnvelope() {
        var envelope = EventEnvelope.of(
                "score.updated", 1, "scoring", "corr-1", Instant.parse("2026-11-18T12:00:00Z"),
                Map.of("driverId", "d-1"));
        JsonNode json = MAPPER.valueToTree(envelope);
        assertThat(schema("envelope").validate(json)).isEmpty();
    }
}
