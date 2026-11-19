package com.qnt.safedrive.scoring;

import com.qnt.safedrive.commons.CorrelationId;
import com.qnt.safedrive.commons.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes trip.completed. driving.event is deliberately not consumed: the trip summary already carries
 * the event counts, and replacing the trip revision replaces them too.
 */
@Component
class TripCompletedListener {

    static final String TOPIC = "trip.completed";

    private final ScoringService service;
    private final JsonMapper mapper;
    private final JavaType type;

    TripCompletedListener(ScoringService service, JsonMapper mapper) {
        this.service = service;
        this.mapper = mapper;
        this.type = mapper.getTypeFactory().constructParametricType(EventEnvelope.class, TripCompleted.class);
    }

    @KafkaListener(topics = TOPIC)
    void onMessage(ConsumerRecord<String, String> record) {
        EventEnvelope<TripCompleted> event = parse(record.value());
        try (CorrelationId.Scope ignored = CorrelationId.bind(event.correlationId())) {
            service.handle(event);
        }
    }

    /** Anything that is not a valid trip.completed v1 event can never succeed, so it is not retried. */
    EventEnvelope<TripCompleted> parse(String json) {
        EventEnvelope<TripCompleted> event;
        try {
            event = mapper.readValue(json, type);
        } catch (JacksonException | NullPointerException | IllegalArgumentException e) {
            throw new UnprocessableEventException("not a readable trip.completed event: " + e.getMessage(), e);
        }
        TripCompleted p = event.payload();
        if (!TOPIC.equals(event.type()) || event.version() != 1) {
            throw new UnprocessableEventException("unexpected event " + event.type() + " v" + event.version());
        }
        if (blank(p.tripId()) || blank(p.driverId()) || p.revision() < 1 || p.endedAt() == null
                || p.distanceKm() < 0 || p.eventCounts() == null) {
            throw new UnprocessableEventException(
                    "trip.completed payload is missing tripId, driverId, revision, endedAt, distanceKm or eventCounts");
        }
        return event;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
