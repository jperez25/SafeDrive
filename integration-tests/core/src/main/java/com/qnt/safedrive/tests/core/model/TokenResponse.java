package com.qnt.safedrive.tests.core.model;

import lombok.Builder;
import lombok.With;

/** Body of the gateway token endpoint. */
@With
@Builder(toBuilder = true)
public record TokenResponse(String accessToken, String tokenType, Long expiresIn, String scope) {}
