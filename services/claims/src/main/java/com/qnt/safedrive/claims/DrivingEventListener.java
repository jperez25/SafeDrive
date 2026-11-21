package com.qnt.safedrive.claims;

import com.qnt.safedrive.commons.CorrelationId;
import com.qnt.safedrive.commons.EventEnvelope;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

@Component
class DrivingEventListener {

    static final String TOPIC = "driving.event";
    static final String CRASH = "crash";
    private static final Set<String> SEVERITIES = Set.of("low", "medium", "high");

    private final ClaimService service;
    private final JsonMapper mapper;
    private final JavaType type;

    DrivingEventListener(ClaimService service, JsonMapper mapper) {
        this.service = service;
        this.mapper = mapper;
        this.type = mapper.getTypeFactory().constructParametricType(EventEnvelope.class, DrivingEvent.class);
    }

    @KafkaListener(topics = TOPIC)
    void onMessage(ConsumerRecord<String, String> record) {
        EventEnvelope<DrivingEvent> event = parse(record.value());
        if (!CRASH.equals(event.payload().kind())) {
            return;   // every other kind is for scoring, not for claims
        }
        try (CorrelationId.Scope ignored = CorrelationId.bind(event.correlationId())) {
            service.onCrash(event);
        }
    }

    /** Anything that is not a valid driving.event v1 can never succeed, so it is not retried. */
    EventEnvelope<DrivingEvent> parse(String json) {
        EventEnvelope<DrivingEvent> event;
        try {
            event = mapper.readValue(json, type);
        } catch (JacksonException | NullPointerException | IllegalArgumentException e) {
            throw new UnprocessableEventException("not a readable driving.event event: " + e.getMessage(), e);
        }
        DrivingEvent p = event.payload();
        if (!TOPIC.equals(event.type()) || event.version() != 1) {
            throw new UnprocessableEventException("unexpected event " + event.type() + " v" + event.version());
        }
        if (p.driverId() == null || p.driverId().isBlank() || p.tripId() == null || p.tripId().isBlank()
                || p.kind() == null || p.kind().isBlank() || p.ts() == null || !SEVERITIES.contains(p.severity())) {
            throw new UnprocessableEventException(
                    "driving.event payload needs driverId, tripId, kind, ts and a severity of low, medium or high");
        }
        return event;
    }
}
