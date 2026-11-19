package com.qnt.safedrive.tests.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import com.qnt.safedrive.tests.core.utils.Json;
import com.qnt.safedrive.tests.core.validator.Validator;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import tools.jackson.databind.node.ObjectNode;

/** The validators accept the contract examples and reject broken events: proves they can fail. */
public class EventValidatorTest {

    private static final Path EXAMPLES = Path.of("..", "..", "docs", "contracts", "events", "examples");

    @DataProvider
    public Object[][] events() {
        return new Object[][] {
            {"trip.raw", "telemetry-ingest"},
            {"trip.completed", "trip-processor"},
            {"driving.event", "trip-processor"},
            {"score.updated", "scoring"},
            {"premium.adjusted", "policy-rating"}
        };
    }

    @Test(dataProvider = "events")
    public void exampleIsValid(String type, String source) throws Exception {
        Validator.with(message(example(type)))
                .envelope()
                .hasType(type)
                .hasSource(source)
                .hasCorrelationId("corr-example-1")
                .hasEventId()
                .matchesContract();
    }

    @Test(dataProvider = "events")
    public void wrongCorrelationIdFails(String type, String source) throws Exception {
        Envelope envelope = example(type);
        assertThatThrownBy(() -> Validator.with(message(envelope)).envelope().hasCorrelationId("other"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("correlationId");
    }

    @Test
    public void unknownPayloadFieldFailsTheContract() throws Exception {
        ObjectNode json = (ObjectNode) Json.tree(Files.readString(EXAMPLES.resolve("trip.completed.v1.json")));
        ((ObjectNode) json.get("payload")).put("surprise", 1);
        Envelope envelope = Json.read(json.toString(), Envelope.class);
        assertThatThrownBy(() -> Validator.with(message(envelope)).envelope().matchesContract())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("contract violations");
    }

    @Test
    public void payloadChildChecksItsFields() throws Exception {
        Envelope envelope = example("trip.completed");
        Validator.with(message(envelope)).envelope()
                .tripCompletedPayload().hasTripId("t-1").hasDriverId("d-1").hasRevision(1).hasNonNegativeDuration();
        assertThatThrownBy(() -> Validator.with(message(envelope)).envelope().tripCompletedPayload().hasRevision(2))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("revision");
    }

    @Test
    public void wrongKeyFails() throws Exception {
        assertThatThrownBy(() -> Validator.with(message(example("trip.raw"))).hasKey("other"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Kafka key");
    }

    private static TopicReader.Message message(Envelope envelope) {
        return new TopicReader.Message(envelope.type(), "trip-1", envelope);
    }

    private static Envelope example(String type) throws Exception {
        return Json.read(Files.readString(EXAMPLES.resolve(type + ".v1.json")), Envelope.class);
    }
}
