package com.qnt.safedrive.tests.telemetryingest.batch;

import com.qnt.safedrive.tests.telemetryingest.AbstractIngestTest;
import io.qameta.allure.Feature;

/** Shared setup for the batch-ingest feature (POST /v1/telemetry/batches). */
@Feature("Batch ingest")
public abstract class AbstractBatchIngestTest extends AbstractIngestTest {}
