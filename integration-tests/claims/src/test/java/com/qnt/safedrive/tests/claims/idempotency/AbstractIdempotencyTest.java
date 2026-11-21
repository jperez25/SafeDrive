package com.qnt.safedrive.tests.claims.idempotency;

import com.qnt.safedrive.tests.claims.AbstractClaimsTest;
import io.qameta.allure.Feature;

/** Shared setup for the idempotency feature: delivery is at-least-once, so a crash never opens two claims. */
@Feature("Idempotency")
public abstract class AbstractIdempotencyTest extends AbstractClaimsTest {}
