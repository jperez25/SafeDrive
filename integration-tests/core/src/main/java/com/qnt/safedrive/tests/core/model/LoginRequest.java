package com.qnt.safedrive.tests.core.model;

import lombok.Builder;
import lombok.With;

/** Body of the gateway token request. The driver ID travels in a header, not here. */
@With
@Builder(toBuilder = true)
public record LoginRequest(String email, String password) {}
