package com.qnt.safedrive.tests.policyrating.premiumcalculation;

import com.qnt.safedrive.tests.policyrating.AbstractRatingTest;
import io.qameta.allure.Feature;

/**
 * Shared setup for the premium calculation feature: the score band picks a percentage that is always applied to the
 * base premium (default 120.00), never on top of the previous premium.
 */
@Feature("Premium calculation")
public abstract class AbstractPremiumCalculationTest extends AbstractRatingTest {}
