package com.qnt.safedrive.tests.claims.claimsapi;

import com.qnt.safedrive.tests.claims.AbstractClaimsTest;
import io.qameta.allure.Feature;

/** Shared setup for the claims API feature ({@code GET /v1/drivers/{driverId}/claims} and {@code GET /v1/claims/{claimId}}). */
@Feature("Claims API")
public abstract class AbstractClaimsApiTest extends AbstractClaimsTest {}
