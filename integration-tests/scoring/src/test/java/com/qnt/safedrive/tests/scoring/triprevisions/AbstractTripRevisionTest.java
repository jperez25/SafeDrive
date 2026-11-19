package com.qnt.safedrive.tests.scoring.triprevisions;

import com.qnt.safedrive.tests.scoring.AbstractScoringTest;
import io.qameta.allure.Feature;

/**
 * Shared setup for the Trip revisions feature. When trip-processor sees late data it republishes the trip with a
 * higher {@code revision}; scoring must replace the old revision, not add the trip a second time.
 */
@Feature("Trip revisions")
public abstract class AbstractTripRevisionTest extends AbstractScoringTest {}
