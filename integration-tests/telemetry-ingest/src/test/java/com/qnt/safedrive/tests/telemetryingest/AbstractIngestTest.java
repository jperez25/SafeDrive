package com.qnt.safedrive.tests.telemetryingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.AbstractTest;
import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import io.qameta.allure.Epic;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Shared setup for every telemetry-ingest feature: reading what the service published to {@code trip.raw}. */
@Epic("telemetry-ingest")
public abstract class AbstractIngestTest extends AbstractTest {

    /** How long to watch for an event that must not appear (publishing is asynchronous, so one look is not enough). */
    private static final Duration NOTHING_MORE_WINDOW = Duration.ofSeconds(5);

    /** Events on {@code trip.raw} for this correlation ID that carry this batch. */
    protected List<TopicReader.Message> tripRawFor(Batch batch, String correlationId) {
        return tracer.events(Topics.TRIP_RAW, correlationId).stream()
                .filter(m -> batch.batchId().equals(m.envelope().payloadText("batchId")))
                .toList();
    }

    /** Watches the window for more than {@code count} events of the batch, then checks there are exactly {@code count}. */
    protected void assertPublishedTimes(int count, Batch batch, String correlationId) {
        waiter.staysEmpty("more than " + count + " trip.raw event(s) for batch " + batch.batchId(), NOTHING_MORE_WINDOW, () -> {
            int found = tripRawFor(batch, correlationId).size();
            return found > count ? Optional.of(found) : Optional.empty();
        });
        assertThat(tripRawFor(batch, correlationId)).as("trip.raw events for batch %s", batch.batchId()).hasSize(count);
    }
}
