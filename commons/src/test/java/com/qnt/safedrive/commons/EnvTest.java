package com.qnt.safedrive.commons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvTest {

    @Test
    void getFallsBackToDefaultWhenMissingOrBlank() {
        assertThat(Env.get(Map.of(), "X", "d")).isEqualTo("d");
        assertThat(Env.get(Map.of("X", " "), "X", "d")).isEqualTo("d");
        assertThat(Env.get(Map.of("X", "v"), "X", "d")).isEqualTo("v");
    }

    @Test
    void requireFailsWhenMissing() {
        assertThat(Env.require(Map.of("X", "v"), "X")).isEqualTo("v");
        assertThatThrownBy(() -> Env.require(Map.of(), "X"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("X");
    }
}
