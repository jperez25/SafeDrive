package com.qnt.safedrive.tests.policyrating.premiumapi;

import com.qnt.safedrive.tests.policyrating.AbstractRatingTest;
import io.qameta.allure.Feature;

/**
 * Shared setup for the Premium API feature ({@code GET /v1/drivers/{driverId}/premium}). The rating is written in the
 * same transaction as its outbox row, so once {@code premium.adjusted} is on the topic the API already serves it.
 */
@Feature("Premium API")
public abstract class AbstractPremiumApiTest extends AbstractRatingTest {}
