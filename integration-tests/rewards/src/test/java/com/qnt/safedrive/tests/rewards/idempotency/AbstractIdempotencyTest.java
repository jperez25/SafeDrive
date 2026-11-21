package com.qnt.safedrive.tests.rewards.idempotency;

import com.qnt.safedrive.tests.rewards.AbstractRewardsTest;
import io.qameta.allure.Feature;

/** Shared setup for the idempotency feature: delivery is at-least-once, so a reward is never granted or sent twice. */
@Feature("Idempotency")
public abstract class AbstractIdempotencyTest extends AbstractRewardsTest {}
