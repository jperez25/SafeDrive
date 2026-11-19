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
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SampleValidationNegativeTest extends AbstractSampleValidationTest {

    @DataProvider
    public Object[][] invalidSamples() {
        return new Object[][] {
            {"latitude above 90", (UnaryOperator<Sample>) s -> s.withLatitude(IngestLimits.MAX_LATITUDE + 0.1), "lat"},
            {"latitude below -90", (UnaryOperator<Sample>) s -> s.withLatitude(-IngestLimits.MAX_LATITUDE - 0.1), "lat"},
            {"longitude above 180", (UnaryOperator<Sample>) s -> s.withLongitude(IngestLimits.MAX_LONGITUDE + 0.1), "lon"},
            {"longitude below -180", (UnaryOperator<Sample>) s -> s.withLongitude(-IngestLimits.MAX_LONGITUDE - 0.1), "lon"},
            {"negative speed", (UnaryOperator<Sample>) s -> s.withSpeedKilometersPerHour(-1.0), "speedKmh"},
            {"speed above 300", (UnaryOperator<Sample>) s -> s.withSpeedKilometersPerHour(IngestLimits.MAX_SPEED_KILOMETERS_PER_HOUR + 0.1), "speedKmh"},
            {"acceleration above 50", (UnaryOperator<Sample>) s -> s.withAccelerationMetersPerSecondSquared(IngestLimits.MAX_ACCELERATION_METERS_PER_SECOND_SQUARED + 0.1), "accelMs2"},
            {"acceleration below -50", (UnaryOperator<Sample>) s -> s.withAccelerationMetersPerSecondSquared(-IngestLimits.MAX_ACCELERATION_METERS_PER_SECOND_SQUARED - 0.1), "accelMs2"},
            {"negative heading", (UnaryOperator<Sample>) s -> s.withHeading(-1.0), "heading"},
            {"heading above 360", (UnaryOperator<Sample>) s -> s.withHeading(IngestLimits.MAX_HEADING + 0.1), "heading"},
            {"timestamp too far ahead", (UnaryOperator<Sample>) s -> s.withTimestamp(
                    Instant.now().plus(IngestLimits.MAX_FUTURE.plusMinutes(5)).truncatedTo(ChronoUnit.SECONDS).toString()), "future"},
            {"timestamp too old", (UnaryOperator<Sample>) s -> s.withTimestamp(
                    Instant.now().minus(IngestLimits.MAX_AGE.plusDays(1)).truncatedTo(ChronoUnit.SECONDS).toString()), "too old"},
            {"timestamp not a date", (UnaryOperator<Sample>) s -> s.withTimestamp("yesterday"), "ts"},
            {"timestamp without a zone", (UnaryOperator<Sample>) s -> s.withTimestamp("2026-01-01T10:00:00"), "ts"},
            {"missing timestamp", (UnaryOperator<Sample>) s -> s.withTimestamp(null), "ts"},
            {"missing latitude", (UnaryOperator<Sample>) s -> s.withLatitude(null), "missing field"},
            {"missing longitude", (UnaryOperator<Sample>) s -> s.withLongitude(null), "missing field"},
            {"missing speed", (UnaryOperator<Sample>) s -> s.withSpeedKilometersPerHour(null), "missing field"},
            {"missing acceleration", (UnaryOperator<Sample>) s -> s.withAccelerationMetersPerSecondSquared(null), "missing field"},
            {"missing heading", (UnaryOperator<Sample>) s -> s.withHeading(null), "missing field"},
            {"missing phone flag", (UnaryOperator<Sample>) s -> s.withPhoneInUse(null), "missing field"}
        };
    }

    @Test(groups = Groups.REGRESSION, dataProvider = "invalidSamples",
            description = "A sample that breaks a rule is dropped and reported with its index; the good sample in the batch is kept")
    @Story("Partial rejection")
    public void invalidSampleIsRejected(String problem, UnaryOperator<Sample> break_, String reasonContaining) {
        Batch batch = aBatchWithSecondSample(break_.apply(aSample()));

        Validator.with(ingestClient().postBatchWithRetry(batch, Ids.correlation()))
                .hasStatus(Http.ACCEPTED)
                .ingestBody()
                .hasStatus(Statuses.INGEST_ACCEPTED)
                .hasAcceptedSamples(1)
                .hasRejectedSampleCount(1)
                .hasRejectedSample(1, reasonContaining);
    }

    @Test(groups = Groups.REGRESSION, description = "A batch where every sample is invalid is rejected as a whole and nothing is published")
    @Story("Whole batch rejected")
    public void allSamplesInvalidIsRejected() {
        Batch batch = aBatch().withSamples(List.of(aSample().withLatitude(91.0), aSample().withLongitude(181.0)));
        String correlationId = Ids.correlation();

        Validator.with(ingestClient().postBatch(batch, correlationId))
                .hasStatus(Http.BAD_REQUEST)
                .ingestBody()
                .hasStatus(Statuses.INGEST_INVALID)
                .hasErrorContaining("no valid samples");
        assertPublishedTimes(0, batch, correlationId);
    }

    @Test(groups = Groups.REGRESSION, description = "Samples whose timestamps go backwards are rejected as a whole")
    @Story("Whole batch rejected")
    public void unorderedTimestampsAreRejected() {
        List<Sample> backwards = new ArrayList<>(samples(3));
        Collections.reverse(backwards);

        Validator.with(ingestClient().postBatch(aBatch().withSamples(backwards), Ids.correlation()))
                .hasStatus(Http.BAD_REQUEST)
                .ingestBody()
                .hasStatus(Statuses.INGEST_INVALID)
                .hasErrorContaining("ordered");
    }
}
