package com.qnt.safedrive.tests.e2e.driverjourney;

import com.qnt.safedrive.tests.e2e.AbstractE2ETest;
import io.qameta.allure.Feature;

/**
 * Shared setup for the driver journey feature: what a driver's trips do to their score and premium. Factory trips are
 * short (under 1 km), so scoring floors the distance at 1 km: a clean trip scores 100 and a trip with any event scores 0.
 */
@Feature("Driver journey")
public abstract class AbstractDriverJourneyTest extends AbstractE2ETest {}
