package com.qnt.safedrive.rewards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** What this service consumes must match docs/contracts/events. */
class ContractTest {

    private static final Path EXAMPLES = Path.of("..", "..", "docs", "contracts", "events", "examples");
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private final PremiumAdjustedListener listener = new PremiumAdjustedListener(null, MAPPER);

    private static String example(String name) throws java.io.IOException {
        return Files.readString(EXAMPLES.resolve(name + ".v1.json"));
    }

    @Test
    void consumerReadsThePremiumAdjustedExample() throws Exception {
        var event = listener.parse(example("premium.adjusted"));
        assertThat(event.payload().driverId()).isNotBlank();
        assertThat(event.payload().scoreVersion()).isGreaterThanOrEqualTo(1);
        assertThat(event.payload().band()).isNotBlank();
    }

    @Test
    void garbageIsUnprocessable() {
        assertThatThrownBy(() -> listener.parse("{not json")).isInstanceOf(UnprocessableEventException.class);
        assertThatThrownBy(() -> listener.parse("{}")).isInstanceOf(UnprocessableEventException.class);
    }

    @Test
    void wrongEventTypeIsUnprocessable() throws Exception {
        assertThatThrownBy(() -> listener.parse(example("score.updated")))
                .isInstanceOf(UnprocessableEventException.class);
    }

    @Test
    void payloadWithoutBandIsUnprocessable() throws Exception {
        String json = example("premium.adjusted").replace("\"band\": \"good\"", "\"band\": \"\"");
        assertThatThrownBy(() -> listener.parse(json)).isInstanceOf(UnprocessableEventException.class);
    }
}
