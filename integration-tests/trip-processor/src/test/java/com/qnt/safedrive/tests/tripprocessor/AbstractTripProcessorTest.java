package com.qnt.safedrive.tests.tripprocessor;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.AbstractTest;
import com.qnt.safedrive.tests.core.factory.EnvelopeFactory;
import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import io.qameta.allure.Epic;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Shared setup for every trip-processor feature: sending batches (through ingest, or straight to {@code trip.raw}
 * when ingest would refuse them) and reading what the processor published.
 *
 * <p>The processor writes its driving events to the outbox before the trip summary, and the relay sends them in
 * that order and waits for each acknowledgement, so once a {@code trip.completed} event is readable, the
 * {@code driving.event}s of that revision are readable too: no separate wait for them.
 */
@Epic("trip-processor")
public abstract class AbstractTripProcessorTest extends AbstractTest {

    /** How long to watch for an event that must not appear (publishing is asynchronous, so one look is not enough). */
    protected static final Duration NOTHING_MORE_WINDOW = Duration.ofSeconds(5);

    /** Sends the batch through the ingest API, the way a device does. */
    protected void ingest(Batch batch, String correlationId) {
        ingestClient().postBatchWithRetry(batch, correlationId);
    }

    /** Puts the batch on {@code trip.raw} as a new event, keyed by trip like ingest does; returns the event that was sent. */
    protected Envelope publishTripRaw(Batch batch, String correlationId) {
        Envelope event = EnvelopeFactory.aTripRawEvent(batch, correlationId);
        publisher().send(Topics.TRIP_RAW, batch.tripId(), event);
        return event;
    }

    /** Waits for the given revision of the trip's {@code trip.completed}. */
    protected TopicReader.Message awaitTripCompleted(Batch batch, String correlationId, int revision) {
        return tracer.awaitEvent(Topics.TRIP_COMPLETED, correlationId,
                m -> batch.tripId().equals(m.envelope().payloadText("tripId"))
                        && revision == m.envelope().payload().path("revision").asInt());
    }

    /** {@code trip.completed} events published so far for the trip. */
    protected List<TopicReader.Message> tripCompletedFor(Batch batch, String correlationId) {
        return tracer.events(Topics.TRIP_COMPLETED, correlationId).stream()
                .filter(m -> batch.tripId().equals(m.envelope().payloadText("tripId")))
                .toList();
    }

    /** {@code driving.event}s published so far for the trip. */
    protected List<TopicReader.Message> drivingEventsFor(Batch batch, String correlationId) {
        return tracer.events(Topics.DRIVING_EVENT, correlationId).stream()
                .filter(m -> batch.tripId().equals(m.envelope().payloadText("tripId")))
                .toList();
    }

    /** Watches the window for a {@code trip.completed} beyond {@code count}, then checks there are exactly {@code count}. */
    protected void assertTripCompletedTimes(int count, Batch batch, String correlationId) {
        waiter.staysEmpty("more than " + count + " trip.completed event(s) for trip " + batch.tripId(), NOTHING_MORE_WINDOW, () -> {
            int found = tripCompletedFor(batch, correlationId).size();
            return found > count ? Optional.of(found) : Optional.empty();
        });
        assertThat(tripCompletedFor(batch, correlationId)).as("trip.completed events for trip %s", batch.tripId()).hasSize(count);
    }

    protected static long secondsBetween(String from, String to) {
        return Duration.between(Instant.parse(from), Instant.parse(to)).toSeconds();
    }
}
