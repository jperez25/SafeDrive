package com.qnt.safedrive.tests.core.validator;

import com.qnt.safedrive.tests.core.trace.TopicReader;
import io.restassured.response.Response;

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
}
