package com.qnt.safedrive.tests.tripprocessor.tripassembly;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.nextOf;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

/** Delivery is at-least-once: the same data arriving again must not change the result. These inputs bypass ingest. */
public class TripAssemblyNegativeTest extends AbstractTripAssemblyTest {

    @Test(groups = Groups.REGRESSION, description = "The same event delivered twice (same event ID) gives one trip.completed")
    @Story("Duplicates")
    public void sameEventDeliveredTwiceIsProcessedOnce() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();
        Envelope event = publishTripRaw(batch, correlationId);
        awaitTripCompleted(batch, correlationId, 1);

        publisher().send(Topics.TRIP_RAW, batch.tripId(), event);

        assertTripCompletedTimes(1, batch, correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "The same batch sequence resent under a new event ID after the trip closed is ignored")
    @Story("Duplicates")
    public void sameBatchSequenceWithNewEventIdIsIgnored() {
        Batch batch = aBatch();
        String correlationId = Ids.correlation();
        publishTripRaw(batch, correlationId);
        awaitTripCompleted(batch, correlationId, 1);

        publishTripRaw(batch.withBatchId(Ids.batch()), correlationId);

        assertTripCompletedTimes(1, batch, correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "A late batch delivered twice under different event IDs produces one revision, not two")
    @Story("Duplicates")
    public void lateBatchDeliveredTwiceIsRepublishedOnce() {
        Batch first = aBatch();
        Batch late = nextOf(first);
        String correlationId = Ids.correlation();
        publishTripRaw(first, correlationId);
        awaitTripCompleted(first, correlationId, 1);

        publishTripRaw(late, correlationId);
        awaitTripCompleted(first, correlationId, 2);
        publishTripRaw(late.withBatchId(Ids.batch()), correlationId);

        assertTripCompletedTimes(2, first, correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "A batch with a gap (sequence 2, nothing at 1) does not close the trip even though it says it is the last")
    @Story("Out of order")
    public void batchWithAGapDoesNotCloseTheTrip() {
        Batch first = aBatch().withLast(false);
        Batch afterGap = nextOf(nextOf(first)); // sequence 2, marked last
        String correlationId = Ids.correlation();

        publishTripRaw(first, correlationId);
        publishTripRaw(afterGap, correlationId);

        assertTripCompletedTimes(0, first, correlationId);
    }
}
