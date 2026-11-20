package com.qnt.safedrive.tests.load;

import com.qnt.safedrive.tests.core.factory.BatchFactory;
import com.qnt.safedrive.tests.core.model.Batch;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.utils.Json;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

/** Virtual drivers: every call builds a fresh valid batch with new IDs, the same way the functional tests do. */
final class Feeders {

    private Feeders() {}

    /** Keys: {@code body} (batch JSON), {@code changedBody} (same batch ID, different content), {@code driverId}, {@code correlationId}. */
    static Iterator<Map<String, Object>> drivers() {
        return Stream.generate(() -> {
                    Batch batch = BatchFactory.aBatch();
                    return Map.<String, Object>of(
                            "body", Json.write(batch),
                            "changedBody", Json.write(batch.withDeviceId(Ids.device())),
                            "driverId", batch.driverId(),
                            "correlationId", Ids.correlation());
                })
                .iterator();
    }
}
