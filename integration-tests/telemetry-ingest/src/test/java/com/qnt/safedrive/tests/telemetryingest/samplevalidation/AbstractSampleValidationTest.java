package com.qnt.safedrive.tests.telemetryingest.samplevalidation;

import static com.qnt.safedrive.tests.core.factory.BatchFactory.aBatch;
import static com.qnt.safedrive.tests.core.factory.BatchFactory.samples;

import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.model.Sample;
import com.qnt.safedrive.tests.telemetryingest.AbstractIngestTest;
import io.qameta.allure.Feature;
import java.util.List;

/** Shared setup for per-sample validation: samples that break a rule are dropped and reported, the rest go on. */
@Feature("Sample validation")
public abstract class AbstractSampleValidationTest extends AbstractIngestTest {

    /** A batch of one good sample (index 0) followed by the given one (index 1), so the reported index is known. */
    protected static Batch aBatchWithSecondSample(Sample second) {
        return aBatch().withSamples(List.of(samples(1).getFirst(), second));
    }
}
