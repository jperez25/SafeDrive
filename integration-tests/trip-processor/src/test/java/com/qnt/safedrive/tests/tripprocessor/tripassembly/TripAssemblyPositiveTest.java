package com.qnt.safedrive.tests.tripprocessor.tripassembly;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.nextOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.constants.EventTypes;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

public class TripAssemblyPositiveTest extends AbstractTripAssemblyTest {

    @Test(groups = Groups.SMOKE, description = "A one-batch trip sent through ingest comes out as one valid trip.completed, keyed by driver")
    @Story("Complete a trip")
    public void singleBatchTripCompletes() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();

        ingest(batch, correlationId);

        TopicReader.Message completed = awaitTripCompleted(batch, correlationId, 1);
        Validator.with(completed)
                .hasTopic(Topics.TRIP_COMPLETED)
                .hasKey(batch.driverId())
                .envelope()
                .hasType(EventTypes.TRIP_COMPLETED)
                .hasSource(Services.TRIP_PROCESSOR)
                .hasCorrelationId(correlationId)
                .hasEventId()
                .matchesContract()
                .tripCompletedPayload()
                .hasTripId(batch.tripId())
                .hasDriverId(batch.driverId())
                .hasRevision(1)
                .hasStartedAt(batch.samples().getFirst().timestamp())
                .hasEndedAt(batch.samples().getLast().timestamp())
                .hasDurationSeconds(secondsBetween(batch.samples().getFirst().timestamp(), batch.samples().getLast().timestamp()))
                .hasAverageSpeed(50.0)
                .hasMaximumSpeed(50.0)
                .hasPositiveDistance()
                .hasNoDrivingEvents();
    }

    @Test(groups = Groups.REGRESSION, description = "A trip of two batches stays open until the last batch arrives, then covers both batches")
    @Story("Complete a trip")
    public void multiBatchTripClosesWhenTheLastBatchArrives() {
        Batch first = aBatch().withLast(false);
        Batch second = nextOf(first);
        String correlationId = Ids.correlation();

        ingest(first, correlationId);
        assertTripCompletedTimes(0, first, correlationId);

        ingest(second, correlationId);

        Validator.with(awaitTripCompleted(first, correlationId, 1))
                .envelope()
                .matchesContract()
                .tripCompletedPayload()
                .hasStartedAt(first.samples().getFirst().timestamp())
                .hasEndedAt(second.samples().getLast().timestamp())
                .hasDurationSeconds(secondsBetween(first.samples().getFirst().timestamp(), second.samples().getLast().timestamp()));
    }

    @Test(groups = Groups.REGRESSION, description = "Batches that arrive out of order (the last one first) still assemble into one trip in sequence order")
    @Story("Out of order")
    public void outOfOrderBatchesAssembleIntoOneTrip() {
        Batch first = aBatch().withLast(false);
        Batch second = nextOf(first);
        String correlationId = Ids.correlation();

        ingest(second, correlationId);
        assertTripCompletedTimes(0, first, correlationId);

        ingest(first, correlationId);

        Validator.with(awaitTripCompleted(first, correlationId, 1))
                .envelope()
                .tripCompletedPayload()
                .hasRevision(1)
                .hasStartedAt(first.samples().getFirst().timestamp())
                .hasEndedAt(second.samples().getLast().timestamp());
        assertTripCompletedTimes(1, first, correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "A late batch after the trip closed republishes the trip as the next revision, covering the new data")
    @Story("Late data")
    public void lateBatchRepublishesTheTripAsTheNextRevision() {
        Batch first = aBatch();
        Batch late = nextOf(first);
        String correlationId = Ids.correlation();
        ingest(first, correlationId);
        awaitTripCompleted(first, correlationId, 1);

        ingest(late, correlationId);

        TopicReader.Message revised = awaitTripCompleted(first, correlationId, 2);
        Validator.with(revised)
                .hasKey(first.driverId())
                .envelope()
                .hasCorrelationId(correlationId)
                .matchesContract()
                .tripCompletedPayload()
                .hasTripId(first.tripId())
                .hasRevision(2)
                .hasStartedAt(first.samples().getFirst().timestamp())
                .hasEndedAt(late.samples().getLast().timestamp())
                .hasDurationSeconds(secondsBetween(first.samples().getFirst().timestamp(), late.samples().getLast().timestamp()));
        assertThat(revised.envelope().eventId())
                .as("a revision is a new event")
                .isNotEqualTo(awaitTripCompleted(first, correlationId, 1).envelope().eventId());
    }

    @Test(groups = Groups.REGRESSION, description = "Two trips of one driver are completed separately, both keyed by the driver")
    @Story("Complete a trip")
    public void twoTripsOfOneDriverAreCompletedSeparately() {
        Batch one = aBatch();
        Batch two = aBatch().withDriverId(one.driverId());
        String correlationId = Ids.correlation();

        ingest(one, correlationId);
        ingest(two, correlationId);

        Validator.with(awaitTripCompleted(one, correlationId, 1)).hasKey(one.driverId()).envelope().tripCompletedPayload().hasTripId(one.tripId());
        Validator.with(awaitTripCompleted(two, correlationId, 1)).hasKey(one.driverId()).envelope().tripCompletedPayload().hasTripId(two.tripId());
    }

    @Test(groups = Groups.REGRESSION, description = "The correlation ID from the caller reaches trip.completed and the processor's logs")
    @Story("Correlation ID")
    public void correlationIdReachesTripCompletedAndLogs() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();

        ingest(batch, correlationId);

        Validator.with(awaitTripCompleted(batch, correlationId, 1)).envelope().hasCorrelationId(correlationId);
        assertThat(tracer.awaitLog(Services.TRIP_PROCESSOR, correlationId))
                .as("trip-processor log lines")
                .allSatisfy(line -> assertThat(line.json()).contains("\"correlationId\":\"" + correlationId + "\""));
    }
}
