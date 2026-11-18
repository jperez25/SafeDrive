package com.qnt.safedrive.commons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    private static final Instant OCCURRED = Instant.parse("2026-11-18T12:00:00Z");

    @Test
    void ofCreatesUniqueEventIds() {
        var a = EventEnvelope.of("score.updated", 1, "scoring", "corr-1", OCCURRED, Map.of("k", "v"));
        var b = EventEnvelope.of("score.updated", 1, "scoring", "corr-1", OCCURRED, Map.of("k", "v"));
        assertThat(a.eventId()).isNotEqualTo(b.eventId());
        assertThat(a.correlationId()).isEqualTo("corr-1");
        assertThat(a.producedAt()).isNotNull();
    }

    @Test
    void rejectsMissingFields() {
        assertThatThrownBy(() -> EventEnvelope.of("t", 1, "s", null, OCCURRED, "p"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("correlationId");
        assertThatThrownBy(() -> EventEnvelope.of("t", 0, "s", "c", OCCURRED, "p"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
