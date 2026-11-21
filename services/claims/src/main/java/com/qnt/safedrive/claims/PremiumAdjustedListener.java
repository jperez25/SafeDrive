package com.qnt.safedrive.claims;

import com.qnt.safedrive.commons.CorrelationId;
import com.qnt.safedrive.commons.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

@Component
class PremiumAdjustedListener {

    static final String TOPIC = "premium.adjusted";

    private final ClaimService service;
    private final JsonMapper mapper;
    private final JavaType type;

    PremiumAdjustedListener(ClaimService service, JsonMapper mapper) {
        this.service = service;
        this.mapper = mapper;
        this.type = mapper.getTypeFactory().constructParametricType(EventEnvelope.class, PremiumAdjusted.class);
    }

    @KafkaListener(topics = TOPIC)
    void onMessage(ConsumerRecord<String, String> record) {
        EventEnvelope<PremiumAdjusted> event = parse(record.value());
        try (CorrelationId.Scope ignored = CorrelationId.bind(event.correlationId())) {
            service.onPolicy(event);
        }
    }

    /** Anything that is not a valid premium.adjusted v1 event can never succeed, so it is not retried. */
    EventEnvelope<PremiumAdjusted> parse(String json) {
        EventEnvelope<PremiumAdjusted> event;
        try {
            event = mapper.readValue(json, type);
        } catch (JacksonException | NullPointerException | IllegalArgumentException e) {
            throw new UnprocessableEventException("not a readable premium.adjusted event: " + e.getMessage(), e);
        }
        PremiumAdjusted p = event.payload();
        if (!TOPIC.equals(event.type()) || event.version() != 1) {
            throw new UnprocessableEventException("unexpected event " + event.type() + " v" + event.version());
        }
        if (p.driverId() == null || p.driverId().isBlank() || p.scoreVersion() < 1
                || p.band() == null || p.band().isBlank()) {
            throw new UnprocessableEventException(
                    "premium.adjusted payload needs driverId, scoreVersion >= 1 and band");
        }
        return event;
    }
}
