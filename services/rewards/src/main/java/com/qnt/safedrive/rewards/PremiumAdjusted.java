package com.qnt.safedrive.rewards;

import java.math.BigDecimal;

/** Payload of premium.adjusted v1 (docs/contracts/events). */
public record PremiumAdjusted(String driverId, int scoreVersion, BigDecimal basePremium, double adjustmentPct,
                              BigDecimal newPremium, String band) {}
