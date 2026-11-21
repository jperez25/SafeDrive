package com.qnt.safedrive.claims;

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

    private final DrivingEventListener driving = new DrivingEventListener(null, MAPPER);
    private final PremiumAdjustedListener premium = new PremiumAdjustedListener(null, MAPPER);

    private static String example(String name) throws java.io.IOException {
        return Files.readString(EXAMPLES.resolve(name + ".v1.json"));
    }

    @Test
    void consumerReadsTheDrivingEventExample() throws Exception {
        var event = driving.parse(example("driving.event"));
        assertThat(event.payload().driverId()).isNotBlank();
        assertThat(event.payload().tripId()).isNotBlank();
        assertThat(event.payload().ts()).isNotNull();
    }

    @Test
    void consumerReadsTheCrashVariantOfTheExample() throws Exception {
        var event = driving.parse(example("driving.event").replace("\"hard_brake\"", "\"crash\""));
        assertThat(event.payload().kind()).isEqualTo(DrivingEventListener.CRASH);
    }

    @Test
    void consumerReadsThePremiumAdjustedExample() throws Exception {
        var event = premium.parse(example("premium.adjusted"));
        assertThat(event.payload().driverId()).isNotBlank();
        assertThat(event.payload().scoreVersion()).isGreaterThanOrEqualTo(1);
        assertThat(event.payload().newPremium()).isNotNull();
    }

    @Test
    void garbageIsUnprocessable() {
        assertThatThrownBy(() -> driving.parse("{not json")).isInstanceOf(UnprocessableEventException.class);
        assertThatThrownBy(() -> driving.parse("{}")).isInstanceOf(UnprocessableEventException.class);
        assertThatThrownBy(() -> premium.parse("{not json")).isInstanceOf(UnprocessableEventException.class);
        assertThatThrownBy(() -> premium.parse("{}")).isInstanceOf(UnprocessableEventException.class);
    }

    @Test
    void wrongEventTypeIsUnprocessable() throws Exception {
        assertThatThrownBy(() -> driving.parse(example("score.updated")))
                .isInstanceOf(UnprocessableEventException.class);
        assertThatThrownBy(() -> premium.parse(example("driving.event")))
                .isInstanceOf(UnprocessableEventException.class);
    }

    @Test
    void drivingEventWithoutTripOrWithUnknownSeverityIsUnprocessable() throws Exception {
        assertThatThrownBy(() -> driving.parse(example("driving.event").replace("\"tripId\": \"t-1\"", "\"tripId\": \"\"")))
                .isInstanceOf(UnprocessableEventException.class);
        assertThatThrownBy(() -> driving.parse(example("driving.event").replace("\"medium\"", "\"extreme\"")))
                .isInstanceOf(UnprocessableEventException.class);
    }
}
