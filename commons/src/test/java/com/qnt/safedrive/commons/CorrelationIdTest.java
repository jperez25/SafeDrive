package com.qnt.safedrive.commons;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorrelationIdTest {

    @Test
    void orGenerateKeepsIncomingId() {
        assertThat(CorrelationId.orGenerate("abc-123")).isEqualTo("abc-123");
    }

    @Test
    void orGenerateCreatesIdWhenMissing() {
        assertThat(CorrelationId.orGenerate(null)).isNotBlank();
        assertThat(CorrelationId.orGenerate("  ")).isNotBlank();
    }

    @Test
    void bindSetsAndRestoresLogContext() {
        assertThat(CorrelationId.current()).isNull();
        try (var outer = CorrelationId.bind("outer")) {
            assertThat(CorrelationId.current()).isEqualTo("outer");
            try (var inner = CorrelationId.bind("inner")) {
                assertThat(CorrelationId.current()).isEqualTo("inner");
            }
            assertThat(CorrelationId.current()).isEqualTo("outer");
        }
        assertThat(CorrelationId.current()).isNull();
    }
}
