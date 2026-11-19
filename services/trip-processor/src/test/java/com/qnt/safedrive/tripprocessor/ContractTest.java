package com.qnt.safedrive.tripprocessor;

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
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** What this service consumes and publishes must match docs/contracts/events. */
class ContractTest {

    private static final Path EVENTS = Path.of("..", "..", "docs", "contracts", "events");
    private static final String ID_PREFIX = "https://contracts.safedrive.local/events/";
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDialect(
            Dialects.getDraft202012(), builder -> builder.schemas(ContractTest::readSchema));

    private final TripRawListener listener = new TripRawListener(null, MAPPER);

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

    private static void assertValid(String type, Object payload) {
        var event = EventEnvelope.of(type, 1, "trip-processor", "c-1", Samples.T0, payload);
        Schema schema = REGISTRY.getSchema(SchemaLocation.of(ID_PREFIX + type + ".v1.schema.json"));
        assertThat(schema.validate(MAPPER.writeValueAsString(event), InputFormat.JSON)).isEmpty();
    }

    @Test
    void consumerReadsTheTripRawExample() throws Exception {
        var event = listener.parse(example("trip.raw"));
        assertThat(event.payload().tripId()).isNotBlank();
        assertThat(event.payload().samples()).isNotEmpty();
        assertThat(event.payload().samples().getFirst().ts()).isNotNull();
    }

    @Test
    void publishedTripCompletedMatchesTheContract() {
        assertValid("trip.completed", new Published.TripCompleted("t-1", "d-1", 2, Samples.T0,
                Samples.T0.plusSeconds(60), 1.5, 60, 40.5, 80, Map.of("hard_brake", 1, "speeding", 0)));
    }

    @Test
    void publishedDrivingEventMatchesTheContract() {
        assertValid("driving.event", new Published.DrivingEvent(
                "t-1", "d-1", "hard_brake", "medium", Samples.T0, 41.88, -87.63));
    }

    @Test
    void garbageIsUnprocessable() {
        assertThatThrownBy(() -> listener.parse("{not json")).isInstanceOf(UnprocessableEventException.class);
        assertThatThrownBy(() -> listener.parse("{}")).isInstanceOf(UnprocessableEventException.class);
    }

    @Test
    void wrongEventTypeIsUnprocessable() throws Exception {
        assertThatThrownBy(() -> listener.parse(example("trip.completed")))
                .isInstanceOf(UnprocessableEventException.class);
    }
}
