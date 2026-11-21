package com.qnt.safedrive.tests.claims.crashclaims;

import com.qnt.safedrive.tests.claims.AbstractClaimsTest;
import io.qameta.allure.Feature;

/** Shared setup for the crash-to-claim feature: a crash opens a claim, with or without the driver's policy known yet. */
@Feature("Crash claims")
public abstract class AbstractCrashClaimTest extends AbstractClaimsTest {}
