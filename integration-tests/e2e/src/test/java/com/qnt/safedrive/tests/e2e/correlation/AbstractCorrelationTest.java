package com.qnt.safedrive.tests.e2e.correlation;

import com.qnt.safedrive.tests.e2e.AbstractE2ETest;
import io.qameta.allure.Feature;

/** Shared setup for the correlation feature: one ID, created at the edge, follows a request through every service. */
@Feature("Correlation")
public abstract class AbstractCorrelationTest extends AbstractE2ETest {}
