package com.qnt.safedrive.tests.core.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.utils.Json;
import com.qnt.safedrive.tests.core.utils.TestEnvironment;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Checks an event against its JSON Schema contract in {@code docs/contracts/events}. */
final class EventContract {

    private static final String ID_PREFIX = "https://contracts.safedrive.local/events/";

    private EventContract() {}

    static void assertValid(Envelope envelope) {
        Path contractsDir = TestEnvironment.current().contractsDir();
        List<Error> errors = schema(envelope.type(), contractsDir).validate(Json.MAPPER.valueToTree(envelope));
        assertThat(errors).as("contract violations for %s", envelope.type()).isEmpty();
    }

    private static Schema schema(String type, Path contractsDir) {
        SchemaRegistry registry = SchemaRegistry.withDialect(
                Dialects.getDraft202012(), builder -> builder.schemas(id -> read(id, contractsDir)));
        return registry.getSchema(SchemaLocation.of(ID_PREFIX + type + ".v1.schema.json"));
    }

    private static String read(String id, Path contractsDir) {
        if (!id.startsWith(ID_PREFIX)) {
            return null;
        }
        Path file = contractsDir.resolve(id.substring(ID_PREFIX.length()));
        try {
            return Files.exists(file) ? Files.readString(file) : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
