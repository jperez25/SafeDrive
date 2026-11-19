package com.qnt.safedrive.tests.core.client;

import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.utils.Json;
import io.qameta.allure.Allure;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.util.ArrayList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Attaches request and response to the Allure report like {@code AllureRestAssured}, but masks secrets first
 * (see {@link Http#REDACTED_HEADERS} and {@link Http#REDACTED_FIELDS}). The request that is actually
 * sent is never changed; only the report text is.
 */
public class RedactingAllureFilter implements Filter {

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification response,
            FilterContext context) {
        Response result = context.next(request, response);
        String title = request.getMethod() + " " + request.getURI();
        Allure.addAttachment(title + " (request)", "text/plain", describe(
                request.getMethod() + " " + request.getURI(), request.getHeaders(), request.getBody()));
        Allure.addAttachment(title + " (response)", "text/plain", describe(
                result.getStatusLine(), result.getHeaders(), result.getBody().asString()));
        return result;
    }

    private static String describe(String firstLine, Headers headers, Object body) {
        StringBuilder text = new StringBuilder(firstLine).append('\n');
        text.append(redactHeaders(headers));
        if (body != null && !body.toString().isEmpty()) {
            text.append('\n').append(redactBody(body.toString()));
        }
        return text.toString();
    }

    public static String redactHeaders(Headers headers) {
        StringBuilder text = new StringBuilder();
        for (Header header : headers) {
            boolean secret = Http.REDACTED_HEADERS.contains(header.getName().toLowerCase());
            text.append(header.getName()).append(": ").append(secret ? Http.REDACTED : header.getValue()).append('\n');
        }
        return text.toString();
    }

    /** Masks secret fields at any depth. A body that is not JSON is returned as is. */
    public static String redactBody(String body) {
        JsonNode tree;
        try {
            tree = Json.tree(body);
        } catch (RuntimeException notJson) {
            return body;
        }
        mask(tree);
        return Json.MAPPER.writeValueAsString(tree);
    }

    private static void mask(JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (String name : new ArrayList<>(object.propertyNames())) {
                if (Http.REDACTED_FIELDS.contains(name.toLowerCase())) {
                    object.put(name, Http.REDACTED);
                } else {
                    mask(object.get(name));
                }
            }
        } else {
            node.forEach(RedactingAllureFilter::mask);
        }
    }
}
