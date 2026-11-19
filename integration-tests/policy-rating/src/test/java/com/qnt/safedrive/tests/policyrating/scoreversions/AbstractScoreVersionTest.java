package com.qnt.safedrive.tests.policyrating.scoreversions;

import com.qnt.safedrive.tests.policyrating.AbstractRatingTest;
import io.qameta.allure.Feature;

/** Shared setup for the score version feature: a score older than the one already applied must not win. */
@Feature("Score versions")
public abstract class AbstractScoreVersionTest extends AbstractRatingTest {}
