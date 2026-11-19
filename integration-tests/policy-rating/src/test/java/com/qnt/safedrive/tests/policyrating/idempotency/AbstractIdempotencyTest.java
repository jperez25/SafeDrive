package com.qnt.safedrive.tests.policyrating.idempotency;

import com.qnt.safedrive.tests.policyrating.AbstractRatingTest;
import io.qameta.allure.Feature;

/** Shared setup for the Idempotency feature: delivery is at-least-once, so the same event may arrive again. */
@Feature("Idempotency")
public abstract class AbstractIdempotencyTest extends AbstractRatingTest {}
