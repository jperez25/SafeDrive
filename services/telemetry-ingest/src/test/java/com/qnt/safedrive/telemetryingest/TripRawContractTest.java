package com.qnt.safedrive.telemetryingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import com.qnt.safedrive.commons.EventEnvelope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The event this service publishes must satisfy docs/contracts/events/trip.raw.v1.schema.json. */
class TripRawContractTest {

    private static final Path EVENTS = Path.of("..", "..", "docs", "contracts", "events");
    private static final String ID_PREFIX = "https://contracts.safedrive.local/events/";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDialect(
            Dialects.getDraft202012(), builder -> builder.schemas(TripRawContractTest::readSchema));

    private static String readSchema(String id) {
        try {
            Path file = EVENTS.resolve(id.substring(ID_PREFIX.length()));
            return id.startsWith(ID_PREFIX) && Files.exists(file) ? Files.readString(file) : null;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void publishedEnvelopeMatchesTheTripRawContract() {
        var batch = new TelemetryBatch("b-1", "t-1", "d-1", "dev-1", 0, true,
                List.of(BatchValidatorTest.sample("2026-11-18T09:59:00Z")));
        var event = EventEnvelope.of("trip.raw", 1, "telemetry-ingest", "c-1", Instant.parse("2026-11-18T09:59:00Z"), batch);

        Schema schema = REGISTRY.getSchema(SchemaLocation.of(ID_PREFIX + "trip.raw.v1.schema.json"));
        assertThat(schema.validate(MAPPER.writeValueAsString(event), com.networknt.schema.InputFormat.JSON)).isEmpty();
    }

    @Test
    void hashIsStableAndContentSensitive() {
        var service = new IngestService(null, MAPPER);
        var a = new TelemetryBatch("b", "t", "d", "dev", 0, false, List.of(BatchValidatorTest.sample("2026-11-18T09:59:00Z")));
        var same = new TelemetryBatch("b", "t", "d", "dev", 0, false, List.of(BatchValidatorTest.sample("2026-11-18T09:59:00Z")));
        var other = new TelemetryBatch("b", "t", "d", "dev", 1, false, List.of(BatchValidatorTest.sample("2026-11-18T09:59:00Z")));
        assertThat(service.hash(a)).isEqualTo(service.hash(same)).isNotEqualTo(service.hash(other));
    }
}
