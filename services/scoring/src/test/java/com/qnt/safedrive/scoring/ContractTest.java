package com.qnt.safedrive.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import com.qnt.safedrive.commons.EventEnvelope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** What this service consumes and publishes must match docs/contracts/events. */
class ContractTest {

    private static final Path EVENTS = Path.of("..", "..", "docs", "contracts", "events");
    private static final String ID_PREFIX = "https://contracts.safedrive.local/events/";
    private static final Instant T0 = Instant.parse("2026-11-18T10:00:00Z");
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDialect(
            Dialects.getDraft202012(), builder -> builder.schemas(ContractTest::readSchema));

    private final TripCompletedListener listener = new TripCompletedListener(null, MAPPER);

    private static String readSchema(String id) {
        try {
            Path file = EVENTS.resolve(id.substring(ID_PREFIX.length()));
            return id.startsWith(ID_PREFIX) && Files.exists(file) ? Files.readString(file) : null;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static String example(String name) throws java.io.IOException {
        return Files.readString(EVENTS.resolve("examples").resolve(name + ".v1.json"));
    }

    @Test
    void consumerReadsTheTripCompletedExample() throws Exception {
        var event = listener.parse(example("trip.completed"));
        assertThat(event.payload().tripId()).isNotBlank();
        assertThat(event.payload().driverId()).isNotBlank();
        assertThat(event.payload().revision()).isGreaterThanOrEqualTo(1);
        assertThat(event.payload().eventCounts()).isNotNull();
    }

    @Test
    void publishedScoreUpdatedMatchesTheContract() {
        var event = EventEnvelope.of("score.updated", 1, "scoring", "c-1", T0,
                new Published.ScoreUpdated("d-1", 3, 82.5, 12, T0));
        Schema schema = REGISTRY.getSchema(SchemaLocation.of(ID_PREFIX + "score.updated.v1.schema.json"));
        assertThat(schema.validate(MAPPER.writeValueAsString(event), InputFormat.JSON)).isEmpty();
    }

    @Test
    void garbageIsUnprocessable() {
        assertThatThrownBy(() -> listener.parse("{not json")).isInstanceOf(UnprocessableEventException.class);
        assertThatThrownBy(() -> listener.parse("{}")).isInstanceOf(UnprocessableEventException.class);
    }

    @Test
    void wrongEventTypeIsUnprocessable() throws Exception {
        assertThatThrownBy(() -> listener.parse(example("score.updated")))
                .isInstanceOf(UnprocessableEventException.class);
    }
}
