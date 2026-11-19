package com.qnt.safedrive.tests.telemetryingest.samplevalidation;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.aSample;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.samples;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.Sample;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.IngestLimits;
import com.qnt.safedrive.tests.core.model.constants.Statuses;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SampleValidationPositiveTest extends AbstractSampleValidationTest {

    @DataProvider
    public Object[][] boundaryValues() {
        return new Object[][] {
            {"latitude 90", (UnaryOperator<Sample>) s -> s.withLatitude(IngestLimits.MAX_LATITUDE)},
            {"latitude -90", (UnaryOperator<Sample>) s -> s.withLatitude(-IngestLimits.MAX_LATITUDE)},
            {"longitude 180", (UnaryOperator<Sample>) s -> s.withLongitude(IngestLimits.MAX_LONGITUDE)},
            {"longitude -180", (UnaryOperator<Sample>) s -> s.withLongitude(-IngestLimits.MAX_LONGITUDE)},
            {"speed 0", (UnaryOperator<Sample>) s -> s.withSpeedKilometersPerHour(0.0)},
            {"speed 300", (UnaryOperator<Sample>) s -> s.withSpeedKilometersPerHour(IngestLimits.MAX_SPEED_KILOMETERS_PER_HOUR)},
            {"acceleration 50", (UnaryOperator<Sample>) s -> s.withAccelerationMetersPerSecondSquared(IngestLimits.MAX_ACCELERATION_METERS_PER_SECOND_SQUARED)},
            {"acceleration -50", (UnaryOperator<Sample>) s -> s.withAccelerationMetersPerSecondSquared(-IngestLimits.MAX_ACCELERATION_METERS_PER_SECOND_SQUARED)},
            {"heading 0", (UnaryOperator<Sample>) s -> s.withHeading(0.0)},
            {"heading 360", (UnaryOperator<Sample>) s -> s.withHeading(IngestLimits.MAX_HEADING)},
            {"timestamp 4 minutes ahead", (UnaryOperator<Sample>) s -> s.withTimestamp(
                    Instant.now().plus(IngestLimits.MAX_FUTURE.minusMinutes(1)).truncatedTo(ChronoUnit.SECONDS).toString())},
            {"timestamp 6 days old", (UnaryOperator<Sample>) s -> s.withTimestamp(
                    Instant.now().minus(IngestLimits.MAX_AGE.minusDays(1)).truncatedTo(ChronoUnit.SECONDS).toString())}
        };
    }

    @Test(groups = Groups.REGRESSION, dataProvider = "boundaryValues",
            description = "A value exactly on its limit is valid: the sample is accepted, not dropped")
    @Story("Limits are inclusive")
    public void boundaryValueIsAccepted(String boundary, UnaryOperator<Sample> change) {
        Batch batch = aBatch().withSamples(List.of(change.apply(aSample())));

        Validator.with(ingestClient().postBatchWithRetry(batch, Ids.correlation()))
                .hasStatus(Http.ACCEPTED)
                .ingestBody()
                .hasStatus(Statuses.INGEST_ACCEPTED)
                .hasAcceptedSamples(1)
                .hasNoRejectedSamples();
    }

    @Test(groups = Groups.REGRESSION,
            description = "Invalid samples are dropped and reported by index; only the clean ones are published, and the event is valid against the contract")
    @Story("Partial rejection")
    public void invalidSamplesAreDroppedAndReported() {
        List<Sample> all = new ArrayList<>(samples(5));
        all.set(1, all.get(1).withLatitude(91.0));
        all.set(3, all.get(3).withSpeedKilometersPerHour(-1.0));
        Batch batch = aBatch().withSamples(all);
        Batch clean = batch.withSamples(List.of(all.get(0), all.get(2), all.get(4)));
        String correlationId = Ids.correlation();

        Validator.with(ingestClient().postBatchWithRetry(batch, correlationId))
                .hasStatus(Http.ACCEPTED)
                .ingestBody()
                .hasStatus(Statuses.INGEST_ACCEPTED)
                .hasAcceptedSamples(3)
                .hasRejectedSampleCount(2)
                .hasRejectedSample(1, "lat")
                .hasRejectedSample(3, "speedKmh");

        Validator.with(tracer.awaitEvents(Topics.TRIP_RAW, correlationId, 1).getFirst())
                .envelope()
                .matchesContract()
                .tripRawPayload()
                .isEqualTo(clean);
    }
}
