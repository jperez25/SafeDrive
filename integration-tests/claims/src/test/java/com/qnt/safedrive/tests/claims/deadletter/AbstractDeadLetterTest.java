package com.qnt.safedrive.tests.claims.deadletter;

import com.qnt.safedrive.tests.claims.AbstractClaimsTest;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import io.qameta.allure.Feature;

/**
 * Shared setup for the dead-letter and replay feature of claims (events it can never handle). Every test uses its own
 * Kafka key, so replays of other tests do not matter: assert "at least" on dead-letter counts.
 */
@Feature("Dead letter and replay")
public abstract class AbstractDeadLetterTest extends AbstractClaimsTest {

    protected TopicReader.RawMessage awaitDeadLetter(String deadLetterTopic, String key) {
        return tracer.awaitRawByKey(deadLetterTopic, key, 1).getFirst();
    }
}
