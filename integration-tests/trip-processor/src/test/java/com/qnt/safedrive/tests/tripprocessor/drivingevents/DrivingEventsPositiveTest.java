package com.qnt.safedrive.tests.tripprocessor.drivingevents;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.aDistractedBatch;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.aHarshAccelerationBatch;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.aHardBrakeBatch;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.aMediumBrakeBatch;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.aSpeedingBatch;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.nextOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.constants.DrivingEventKinds;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Severities;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import java.util.List;
import org.testng.annotations.Test;

public class DrivingEventsPositiveTest extends AbstractDrivingEventsTest {

    private static final int EVENT_INDEX = 2; // the sample BatchFactory changes

    /** Sends the batch, waits for its trip, and returns the single driving.event it produced. */
    private TopicReader.Message theOnlyEventOf(Batch batch, String correlationId, String kind) {
        ingest(batch, correlationId);
        Validator.with(awaitTripCompleted(batch, correlationId, 1))
                .envelope()
                .tripCompletedPayload()
                .hasEventCount(kind, 1);
        List<TopicReader.Message> events = drivingEventsFor(batch, correlationId);
        assertThat(events).as("driving events of trip %s", batch.tripId()).hasSize(1);
        return events.getFirst();
    }

    @Test(groups = Groups.SMOKE, description = "A hard brake is published as a driving.event and counted in trip.completed")
    @Story("Hard brake")
    public void hardBrakeIsDetected() {
        Batch batch = aMediumBrakeBatch();
        String correlationId = Ids.correlation();

        TopicReader.Message event = theOnlyEventOf(batch, correlationId, DrivingEventKinds.HARD_BRAKE);

        assertDrivingEvent(event, batch, correlationId)
                .hasKind(DrivingEventKinds.HARD_BRAKE)
                .hasSeverity(Severities.MEDIUM)
                .isStampedWith(batch.samples().get(EVENT_INDEX));
    }

    @Test(groups = Groups.REGRESSION, description = "A very hard brake is high severity")
    @Story("Hard brake")
    public void veryHardBrakeIsHighSeverity() {
        Batch batch = aHardBrakeBatch();
        String correlationId = Ids.correlation();

        TopicReader.Message event = theOnlyEventOf(batch, correlationId, DrivingEventKinds.HARD_BRAKE);

        assertDrivingEvent(event, batch, correlationId)
                .hasKind(DrivingEventKinds.HARD_BRAKE)
                .hasSeverity(Severities.HIGH)
                .isStampedWith(batch.samples().get(EVENT_INDEX));
    }

    @Test(groups = Groups.REGRESSION, description = "Speeding is detected with the speed band as severity")
    @Story("Speeding")
    public void speedingIsDetected() {
        Batch batch = aSpeedingBatch();
        String correlationId = Ids.correlation();

        TopicReader.Message event = theOnlyEventOf(batch, correlationId, DrivingEventKinds.SPEEDING);

        assertDrivingEvent(event, batch, correlationId)
                .hasKind(DrivingEventKinds.SPEEDING)
                .hasSeverity(Severities.MEDIUM)
                .isStampedWith(batch.samples().get(EVENT_INDEX));
    }

    @Test(groups = Groups.REGRESSION, description = "A speed exactly at the lowest threshold counts, as low severity")
    @Story("Speeding")
    public void speedAtTheLowThresholdIsLowSeverity() {
        Batch batch = withSample(aBatch(), EVENT_INDEX, sample -> sample.withSpeedKilometersPerHour(120.0));
        String correlationId = Ids.correlation();

        TopicReader.Message event = theOnlyEventOf(batch, correlationId, DrivingEventKinds.SPEEDING);

        assertDrivingEvent(event, batch, correlationId).hasSeverity(Severities.LOW);
    }

    @Test(groups = Groups.REGRESSION, description = "Harsh acceleration is detected")
    @Story("Harsh acceleration")
    public void harshAccelerationIsDetected() {
        Batch batch = aHarshAccelerationBatch();
        String correlationId = Ids.correlation();

        TopicReader.Message event = theOnlyEventOf(batch, correlationId, DrivingEventKinds.HARSH_ACCELERATION);

        assertDrivingEvent(event, batch, correlationId)
                .hasKind(DrivingEventKinds.HARSH_ACCELERATION)
                .hasSeverity(Severities.MEDIUM)
                .isStampedWith(batch.samples().get(EVENT_INDEX));
    }

    @Test(groups = Groups.REGRESSION, description = "Using the phone while driving fast is detected as distraction")
    @Story("Distraction")
    public void distractionIsDetected() {
        Batch batch = aDistractedBatch();
        String correlationId = Ids.correlation();

        TopicReader.Message event = theOnlyEventOf(batch, correlationId, DrivingEventKinds.DISTRACTION);

        assertDrivingEvent(event, batch, correlationId)
                .hasKind(DrivingEventKinds.DISTRACTION)
                .hasSeverity(Severities.MEDIUM)
                .isStampedWith(batch.samples().get(EVENT_INDEX));
    }

    @Test(groups = Groups.REGRESSION, description = "Consecutive samples that break one rule are a single event: first sample's time, worst severity")
    @Story("Event grouping")
    public void consecutiveBreachesAreOneEventAtTheWorstSeverity() {
        Batch calm = aBatch();
        Batch batch = withSample(
                withSample(
                        withSample(calm, 1, s -> s.withSpeedKilometersPerHour(125.0)),
                        2, s -> s.withSpeedKilometersPerHour(150.0)),
                3, s -> s.withSpeedKilometersPerHour(130.0));
        String correlationId = Ids.correlation();

        TopicReader.Message event = theOnlyEventOf(batch, correlationId, DrivingEventKinds.SPEEDING);

        assertDrivingEvent(event, batch, correlationId)
                .hasKind(DrivingEventKinds.SPEEDING)
                .hasSeverity(Severities.MEDIUM)
                .isStampedWith(batch.samples().get(1));
    }

    @Test(groups = Groups.REGRESSION, description = "Two separate breaches of one rule are two events")
    @Story("Event grouping")
    public void separateBreachesAreSeparateEvents() {
        Batch batch = withSample(
                withSample(aBatch(), 1, s -> s.withSpeedKilometersPerHour(150.0)),
                3, s -> s.withSpeedKilometersPerHour(150.0));
        String correlationId = Ids.correlation();
        ingest(batch, correlationId);

        Validator.with(awaitTripCompleted(batch, correlationId, 1))
                .envelope()
                .tripCompletedPayload()
                .hasEventCount(DrivingEventKinds.SPEEDING, 2);
        List<TopicReader.Message> events = drivingEventsFor(batch, correlationId);

        assertThat(events).hasSize(2);
        assertDrivingEvent(events.get(0), batch, correlationId).isStampedWith(batch.samples().get(1));
        assertDrivingEvent(events.get(1), batch, correlationId).isStampedWith(batch.samples().get(3));
    }

    @Test(groups = Groups.REGRESSION, description = "Different rules broken in one trip each produce their own event")
    @Story("Event grouping")
    public void differentKindsInOneTripAreCountedSeparately() {
        Batch batch = withSample(
                withSample(aBatch(), 1, s -> s.withSpeedKilometersPerHour(150.0)),
                3, s -> s.withAccelerationMetersPerSecondSquared(-8.0));
        String correlationId = Ids.correlation();
        ingest(batch, correlationId);

        Validator.with(awaitTripCompleted(batch, correlationId, 1))
                .envelope()
                .tripCompletedPayload()
                .hasEventCount(DrivingEventKinds.SPEEDING, 1)
                .hasEventCount(DrivingEventKinds.HARD_BRAKE, 1)
                .hasEventCount(DrivingEventKinds.HARSH_ACCELERATION, 0)
                .hasEventCount(DrivingEventKinds.DISTRACTION, 0);
        assertThat(drivingEventsFor(batch, correlationId))
                .extracting(m -> m.envelope().payloadText("kind"))
                .containsExactlyInAnyOrder(DrivingEventKinds.SPEEDING, DrivingEventKinds.HARD_BRAKE);
    }

    @Test(groups = Groups.REGRESSION,
            description = "A late batch republishes only its new events; the earlier event is not sent again and the counts are cumulative")
    @Story("Late data")
    public void lateBatchPublishesOnlyNewEvents() {
        Batch first = aSpeedingBatch();
        Batch late = withSample(nextOf(first), EVENT_INDEX, s -> s.withAccelerationMetersPerSecondSquared(-8.0));
        String correlationId = Ids.correlation();
        ingest(first, correlationId);
        awaitTripCompleted(first, correlationId, 1);

        ingest(late, correlationId);

        Validator.with(awaitTripCompleted(first, correlationId, 2))
                .envelope()
                .tripCompletedPayload()
                .hasRevision(2)
                .hasEventCount(DrivingEventKinds.SPEEDING, 1)
                .hasEventCount(DrivingEventKinds.HARD_BRAKE, 1);
        assertThat(drivingEventsFor(first, correlationId))
                .extracting(m -> m.envelope().payloadText("kind"))
                .containsExactlyInAnyOrder(DrivingEventKinds.SPEEDING, DrivingEventKinds.HARD_BRAKE);
    }
}
