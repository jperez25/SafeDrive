package com.qnt.safedrive.tests.e2e.pipelineresilience;

import com.qnt.safedrive.tests.e2e.AbstractE2ETest;
import io.qameta.allure.Feature;

/** Shared setup for the pipeline resilience feature: duplicates, out-of-order and late data, and poison events across the whole chain. */
@Feature("Pipeline resilience")
public abstract class AbstractPipelineResilienceTest extends AbstractE2ETest {}
