package com.qnt.safedrive.tests.scoring.scoreapi;

import com.qnt.safedrive.tests.scoring.AbstractScoringTest;
import io.qameta.allure.Feature;

/**
 * Shared setup for the Score API feature ({@code GET /v1/drivers/{driverId}/score}). The score is written in the same
 * transaction as its outbox row, so once {@code score.updated} is on the topic the API already serves that score.
 */
@Feature("Score API")
public abstract class AbstractScoreApiTest extends AbstractScoringTest {}
