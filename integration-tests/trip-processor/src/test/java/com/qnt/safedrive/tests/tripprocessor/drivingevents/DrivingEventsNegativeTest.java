package com.qnt.safedrive.tests.tripprocessor.drivingevents;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.constants.DrivingEventKinds;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

/** Trips that must NOT produce driving events. */
public class DrivingEventsNegativeTest extends AbstractDrivingEventsTest {

    private static final int EVENT_INDEX = 2;

    /** Sends the batch and checks its trip completes with no driving event at all. */
    private void assertNoDrivingEvents(Batch batch) {
        String correlationId = Ids.correlation();
        ingest(batch, correlationId);

        Validator.with(awaitTripCompleted(batch, correlationId, 1)).envelope().tripCompletedPayload().hasNoDrivingEvents();
        assertThat(drivingEventsFor(batch, correlationId)).as("driving events of trip %s", batch.tripId()).isEmpty();
    }

    @Test(groups = Groups.REGRESSION, description = "A calm trip completes with all event counts zero and no driving.event")
    @Story("No false alarms")
    public void calmTripHasNoDrivingEvents() {
        assertNoDrivingEvents(aBatch());
    }

    @Test(groups = Groups.REGRESSION, description = "Values just below every threshold (119 km/h, -3.4 and +2.9 m/s2) are not events")
    @Story("No false alarms")
    public void valuesJustBelowTheThresholdsAreNotEvents() {
        Batch batch = withSample(
                withSample(
                        withSample(aBatch(), 1, s -> s.withSpeedKilometersPerHour(119.0)),
                        2, s -> s.withAccelerationMetersPerSecondSquared(-3.4)),
                3, s -> s.withAccelerationMetersPerSecondSquared(2.9));

        assertNoDrivingEvents(batch);
    }

    @Test(groups = Groups.REGRESSION, description = "Phone in use while almost stopped (below 10 km/h) is not distraction")
    @Story("No false alarms")
    public void phoneInUseWhileSlowIsNotDistraction() {
        Batch batch = withSample(aBatch(), EVENT_INDEX, s -> s.withSpeedKilometersPerHour(5.0).withPhoneInUse(true));

        assertNoDrivingEvents(batch);
    }

    @Test(groups = Groups.REGRESSION, description = "Fast driving without the phone in use is not distraction")
    @Story("No false alarms")
    public void fastDrivingWithoutThePhoneIsNotDistraction() {
        Batch batch = withSample(aBatch(), EVENT_INDEX, s -> s.withSpeedKilometersPerHour(100.0).withPhoneInUse(false));

        assertNoDrivingEvents(batch);
    }

    @Test(groups = Groups.REGRESSION, description = "A hard brake that keeps moving (no stop) is a hard brake, not a crash")
    @Story("No false alarms")
    public void hardBrakeWithoutAStopIsNotACrash() {
        Batch batch = withSample(aBatch(), EVENT_INDEX, s -> s.withAccelerationMetersPerSecondSquared(-35.0));
        String correlationId = Ids.correlation();
        ingest(batch, correlationId);

        Validator.with(awaitTripCompleted(batch, correlationId, 1))
                .envelope()
                .tripCompletedPayload()
                .hasEventCount(DrivingEventKinds.HARD_BRAKE, 1)
                .hasEventCount(DrivingEventKinds.CRASH, 0);
    }
}
