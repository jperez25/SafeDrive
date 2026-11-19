package com.qnt.safedrive.tripprocessor;

import com.qnt.safedrive.commons.CorrelationId;
import com.qnt.safedrive.commons.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

@Component
class TripRawListener {

    static final String TOPIC = "trip.raw";

    private final TripService service;
    private final JsonMapper mapper;
    private final JavaType type;

    TripRawListener(TripService service, JsonMapper mapper) {
        this.service = service;
        this.mapper = mapper;
        this.type = mapper.getTypeFactory().constructParametricType(EventEnvelope.class, TripRaw.class);
    }

    @KafkaListener(topics = TOPIC)
    void onMessage(ConsumerRecord<String, String> record) {
        EventEnvelope<TripRaw> event = parse(record.value());
        try (CorrelationId.Scope ignored = CorrelationId.bind(event.correlationId())) {
            service.handle(event);
        }
    }

    /** Anything that is not a valid trip.raw v1 event can never succeed, so it is not retried. */
    EventEnvelope<TripRaw> parse(String json) {
        EventEnvelope<TripRaw> event;
        try {
            event = mapper.readValue(json, type);
        } catch (JacksonException | NullPointerException | IllegalArgumentException e) {
            throw new UnprocessableEventException("not a readable trip.raw event: " + e.getMessage(), e);
        }
        TripRaw p = event.payload();
        if (!TOPIC.equals(event.type()) || event.version() != 1) {
            throw new UnprocessableEventException("unexpected event " + event.type() + " v" + event.version());
        }
        if (blank(p.tripId()) || blank(p.driverId()) || p.samples() == null || p.samples().isEmpty() || p.batchSeq() < 0) {
            throw new UnprocessableEventException("trip.raw payload is missing tripId, driverId, batchSeq or samples");
        }
        return event;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
