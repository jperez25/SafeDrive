package com.qnt.safedrive.tests.tripprocessor.drivingevents;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.Sample;
import com.qnt.safedrive.tests.core.model.constants.EventTypes;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import com.qnt.safedrive.tests.core.validator.DrivingEventPayloadValidator;
import com.qnt.safedrive.tests.core.validator.Validator;
import com.qnt.safedrive.tests.tripprocessor.AbstractTripProcessorTest;
import io.qameta.allure.Feature;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Shared setup for the driving-event detection feature of trip-processor. */
@Feature("Driving events")
public abstract class AbstractDrivingEventsTest extends AbstractTripProcessorTest {

    /** The batch with the sample at {@code index} changed; every other sample stays as the factory made it. */
    protected static Batch withSample(Batch batch, int index, UnaryOperator<Sample> change) {
        List<Sample> samples = new ArrayList<>(batch.samples());
        samples.set(index, change.apply(samples.get(index)));
        return batch.withSamples(samples);
    }

    /** Checks the master and envelope of a driving.event for this batch and returns its payload validator. */
    protected DrivingEventPayloadValidator assertDrivingEvent(TopicReader.Message event, Batch batch, String correlationId) {
        return Validator.with(event)
                .hasTopic(Topics.DRIVING_EVENT)
                .hasKey(batch.driverId())
                .envelope()
                .hasType(EventTypes.DRIVING_EVENT)
                .hasSource(Services.TRIP_PROCESSOR)
                .hasCorrelationId(correlationId)
                .hasEventId()
                .matchesContract()
                .drivingEventPayload()
                .hasTripId(batch.tripId())
                .hasDriverId(batch.driverId());
    }
}
