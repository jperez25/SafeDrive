package com.qnt.safedrive.tests.scoring.scorecalculation;

import com.qnt.safedrive.tests.scoring.AbstractScoringTest;
import io.qameta.allure.Feature;

/**
 * Shared setup for the Score calculation feature. Formula (docs/architecture/services.md): per trip,
 * 100 minus the weighted events per 100 km (hard_brake 2, harsh_accel 2, speeding 3, distraction 4, crash 25;
 * distance counts as at least 1 km), floored at 0; the driver score is the mean of the last 10 trips.
 */
@Feature("Score calculation")
public abstract class AbstractScoreCalculationTest extends AbstractScoringTest {}
