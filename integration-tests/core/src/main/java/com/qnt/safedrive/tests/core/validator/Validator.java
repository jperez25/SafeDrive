package com.qnt.safedrive.tests.core.validator;

import com.qnt.safedrive.tests.core.trace.TopicReader;
import io.restassured.response.Response;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Entry point of the fluent validators. The master validator checks what is true of any response or
 * message; its methods create the child validators for the body, envelope or payload.
 */
public final class Validator {

    private Validator() {}

    public static ResponseValidator with(Response response) {
        return new ResponseValidator(response);
    }

    public static MessageValidator with(TopicReader.Message message) {
        return new MessageValidator(message);
    }

    public static RawMessageValidator with(TopicReader.RawMessage message) {
        return new RawMessageValidator(message);
    }

    /** Log lines of one service for a request. */
    public static LogLinesValidator withLogLines(List<com.qnt.safedrive.tests.core.trace.LogReader.Line> lines) {
        return new LogLinesValidator(lines);
    }

    /** The notifications the mocked provider received (see {@code NotificationMockClient}). */
    public static NotificationsValidator with(List<JsonNode> notifications) {
        return new NotificationsValidator(notifications);
    }
}
