package com.qnt.safedrive.tests.core.model;

import lombok.Builder;
import lombok.With;

/** Body of a gateway error answer (401, 403, 404, 405, 413 and the 400 of the token endpoint). */
@With
@Builder(toBuilder = true)
public record GatewayError(String status, String message, String correlationId) {}
