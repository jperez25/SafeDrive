package com.qnt.safedrive.policyrating;

import com.qnt.safedrive.commons.CorrelationId;
import com.qnt.safedrive.commons.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

@Component
class ScoreUpdatedListener {

    static final String TOPIC = "score.updated";

    private final RatingService service;
    private final JsonMapper mapper;
    private final JavaType type;

    ScoreUpdatedListener(RatingService service, JsonMapper mapper) {
        this.service = service;
        this.mapper = mapper;
        this.type = mapper.getTypeFactory().constructParametricType(EventEnvelope.class, ScoreUpdated.class);
    }

    @KafkaListener(topics = TOPIC)
    void onMessage(ConsumerRecord<String, String> record) {
        EventEnvelope<ScoreUpdated> event = parse(record.value());
        try (CorrelationId.Scope ignored = CorrelationId.bind(event.correlationId())) {
            service.handle(event);
        }
    }

    /** Anything that is not a valid score.updated v1 event can never succeed, so it is not retried. */
    EventEnvelope<ScoreUpdated> parse(String json) {
        EventEnvelope<ScoreUpdated> event;
        try {
            event = mapper.readValue(json, type);
        } catch (JacksonException | NullPointerException | IllegalArgumentException e) {
            throw new UnprocessableEventException("not a readable score.updated event: " + e.getMessage(), e);
        }
        ScoreUpdated p = event.payload();
        if (!TOPIC.equals(event.type()) || event.version() != 1) {
            throw new UnprocessableEventException("unexpected event " + event.type() + " v" + event.version());
        }
        if (p.driverId() == null || p.driverId().isBlank() || p.scoreVersion() < 1
                || Double.isNaN(p.score()) || p.score() < 0 || p.score() > 100) {
            throw new UnprocessableEventException(
                    "score.updated payload needs driverId, scoreVersion >= 1 and score between 0 and 100");
        }
        return event;
    }
}
