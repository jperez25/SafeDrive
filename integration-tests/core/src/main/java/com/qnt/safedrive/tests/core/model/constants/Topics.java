package com.qnt.safedrive.tests.core.model.constants;

/** Kafka topic names. */
public final class Topics {

    private Topics() {}

    public static final String TRIP_RAW = "trip.raw";
    public static final String TRIP_COMPLETED = "trip.completed";
    public static final String DRIVING_EVENT = "driving.event";
    public static final String SCORE_UPDATED = "score.updated";
    public static final String PREMIUM_ADJUSTED = "premium.adjusted";

    /** Dead-letter topic of a consumer group, e.g. {@code trip.raw.trip-processor.dlt}. */
    public static String deadLetter(String topic, String consumerGroup) {
        return topic + "." + consumerGroup + ".dlt";
    }
}
