package com.qnt.safedrive.tests.scoring.deadletter;

import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import com.qnt.safedrive.tests.scoring.AbstractScoringTest;
import io.qameta.allure.Feature;

/**
 * Shared setup for the dead-letter and replay feature of scoring. Every test uses its own Kafka key (the driver), so
 * it finds its own dead letters among those of parallel tests. Replays move every new dead letter back (see
 * {@code DeadLetterReplayer}), so a count of dead letters for a key can only grow: assert "at least".
 */
@Feature("Dead letter and replay")
public abstract class AbstractDeadLetterTest extends AbstractScoringTest {

    protected static final String DEAD_LETTER_TOPIC = Topics.deadLetter(Topics.TRIP_COMPLETED, Services.SCORING);

    /** Waits until the event with this key is on the dead-letter topic and returns it, value untouched. */
    protected TopicReader.RawMessage awaitDeadLetter(String key) {
        return tracer.awaitRawByKey(DEAD_LETTER_TOPIC, key, 1).getFirst();
    }
}
