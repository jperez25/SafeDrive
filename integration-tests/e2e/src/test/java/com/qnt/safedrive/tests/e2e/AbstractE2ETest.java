package com.qnt.safedrive.tests.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.AbstractTest;
import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import io.qameta.allure.Epic;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared setup for every end-to-end feature. Tests drive only the edge (the ingest API) and read what each
 * service published on its topic and what the read APIs serve, all followed by one correlation ID.
 */
@Epic("e2e")
public abstract class AbstractE2ETest extends AbstractTest {

    /** How long to watch for an event that must not appear (publishing is asynchronous, so one look is not enough). */
    protected static final Duration NOTHING_MORE_WINDOW = Duration.ofSeconds(5);

    /** Sends the batch through the ingest API, the way a device does. */
    protected void ingest(Batch batch, String correlationId) {
        ingestClient().postBatchWithRetry(batch, correlationId);
    }

    /** Waits for the event of the topic with this correlation ID whose payload field has the given value. */
    protected TopicReader.Message awaitEvent(String topic, String correlationId, String payloadField, String value) {
        return tracer.awaitEvent(topic, correlationId, m -> value.equals(m.envelope().payloadText(payloadField)));
    }

    /** Waits for the score of this driver and version. */
    protected TopicReader.Message awaitScoreUpdated(String driverId, String correlationId, int scoreVersion) {
        return tracer.awaitEvent(Topics.SCORE_UPDATED, correlationId,
                m -> driverId.equals(m.envelope().payloadText("driverId"))
                        && scoreVersion == m.envelope().payload().path("scoreVersion").asInt());
    }

    /** Waits for the premium of this driver and score version. */
    protected TopicReader.Message awaitPremiumAdjusted(String driverId, String correlationId, int scoreVersion) {
        return tracer.awaitEvent(Topics.PREMIUM_ADJUSTED, correlationId,
                m -> driverId.equals(m.envelope().payloadText("driverId"))
                        && scoreVersion == m.envelope().payload().path("scoreVersion").asInt());
    }

    /** Number of events on each pipeline topic for this trip and driver, with this correlation ID. */
    protected Map<String, Integer> pipelineEventCounts(Batch batch, String correlationId) {
        return Map.of(
                Topics.TRIP_RAW, countFor(Topics.TRIP_RAW, correlationId, "tripId", batch.tripId()),
                Topics.TRIP_COMPLETED, countFor(Topics.TRIP_COMPLETED, correlationId, "tripId", batch.tripId()),
                Topics.SCORE_UPDATED, countFor(Topics.SCORE_UPDATED, correlationId, "driverId", batch.driverId()),
                Topics.PREMIUM_ADJUSTED, countFor(Topics.PREMIUM_ADJUSTED, correlationId, "driverId", batch.driverId()));
    }

    /**
     * Watches the window for any pipeline topic getting more than the expected events, then checks the exact counts.
     * Call it once the last hop has been seen, so a slow service has nothing left to publish.
     */
    protected void assertPipelineEventCounts(Batch batch, String correlationId, int tripRaw, int tripCompleted, int scoreUpdated, int premiumAdjusted) {
        Map<String, Integer> expected = Map.of(
                Topics.TRIP_RAW, tripRaw,
                Topics.TRIP_COMPLETED, tripCompleted,
                Topics.SCORE_UPDATED, scoreUpdated,
                Topics.PREMIUM_ADJUSTED, premiumAdjusted);
        waiter.staysEmpty("more pipeline events than expected " + expected, NOTHING_MORE_WINDOW, () -> {
            Map<String, Integer> found = pipelineEventCounts(batch, correlationId);
            boolean tooMany = expected.entrySet().stream().anyMatch(e -> found.get(e.getKey()) > e.getValue());
            return tooMany ? Optional.of(found) : Optional.empty();
        });
        assertThat(pipelineEventCounts(batch, correlationId)).as("events per topic").isEqualTo(expected);
    }

    private int countFor(String topic, String correlationId, String payloadField, String value) {
        List<TopicReader.Message> events = tracer.events(topic, correlationId);
        return (int) events.stream().filter(m -> value.equals(m.envelope().payloadText(payloadField))).count();
    }
}
