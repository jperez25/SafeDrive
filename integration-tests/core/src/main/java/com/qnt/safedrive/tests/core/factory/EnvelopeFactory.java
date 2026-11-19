package com.qnt.safedrive.tests.core.factory;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.TripCompleted;
import com.qnt.safedrive.tests.core.model.constants.EventTypes;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.utils.Json;
import java.time.Instant;

/**
 * Valid envelopes with a fresh event ID, as telemetry-ingest would publish them. Returns the {@link Envelope}
 * itself; override with {@code withX}, e.g. {@code aTripRawEvent(batch, correlationId).withVersion(2)}.
 */
public final class EnvelopeFactory {

    private EnvelopeFactory() {}

    /** A {@code trip.completed} event as trip-processor would publish it; each call gets a new event ID. */
    public static Envelope aTripCompletedEvent(TripCompleted trip, String correlationId) {
        Instant now = Instant.now();
        return Envelope.builder()
                .eventId(Ids.event())
                .type(EventTypes.TRIP_COMPLETED)
                .version(1)
                .occurredAt(trip.endedAt() != null ? trip.endedAt() : now.toString())
                .producedAt(now.toString())
                .correlationId(correlationId)
                .source(Services.TRIP_PROCESSOR)
                .payload(Json.MAPPER.valueToTree(trip))
                .build();
    }

    /** A {@code trip.raw} event that carries the batch. Same call with the same batch gives a new event ID. */
    public static Envelope aTripRawEvent(Batch batch, String correlationId) {
        Instant now = Instant.now();
        // A deliberately broken batch may have no samples to take the time from.
        boolean hasSamples = batch.samples() != null && !batch.samples().isEmpty();
        return Envelope.builder()
                .eventId(Ids.event())
                .type(EventTypes.TRIP_RAW)
                .version(1)
                .occurredAt(hasSamples ? batch.samples().getFirst().timestamp() : now.toString())
                .producedAt(now.toString())
                .correlationId(correlationId)
                .source(Services.TELEMETRY_INGEST)
                .payload(Json.MAPPER.valueToTree(batch))
                .build();
    }
}
