package com.qnt.safedrive.commons;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Envelope shared by every event on every topic. Contract: docs/contracts/events/envelope.v1.schema.json.
 */
public record EventEnvelope<T>(
        UUID eventId,
        String type,
        int version,
        Instant occurredAt,
        Instant producedAt,
        String correlationId,
        String source,
        T payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(producedAt, "producedAt");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(payload, "payload");
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
    }

    /** New event with a fresh event ID and producedAt = now. */
    public static <T> EventEnvelope<T> of(
            String type, int version, String source, String correlationId, Instant occurredAt, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(), type, version, occurredAt, Instant.now(), correlationId, source, payload);
    }
}
