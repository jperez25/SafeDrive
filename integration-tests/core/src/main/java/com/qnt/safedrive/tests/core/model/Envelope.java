package com.qnt.safedrive.tests.core.model;

import lombok.Builder;
import lombok.With;
import tools.jackson.databind.JsonNode;

/** The event envelope as read from a topic; the payload stays a tree until a test converts it. */
@With
@Builder(toBuilder = true)
public record Envelope(
        String eventId,
        String type,
        Integer version,
        String occurredAt,
        String producedAt,
        String correlationId,
        String source,
        JsonNode payload) {

    public String payloadText(String field) {
        return payload.path(field).asString();
    }
}
