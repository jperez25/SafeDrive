package com.qnt.safedrive.tests.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.factory.BatchFactory;
import com.qnt.safedrive.tests.core.utils.Json;
import org.testng.annotations.Test;

public class WireNamesTest {

    @Test
    public void sampleKeepsTheContractFieldNamesOnTheWire() {
        var sample = Json.tree(Json.write(BatchFactory.aBatch(1))).path("samples").get(0);
        assertThat(sample.propertyNames())
                .containsExactlyInAnyOrder("ts", "lat", "lon", "speedKmh", "accelMs2", "heading", "phoneInUse");
    }

    @Test
    public void batchSequenceIsSentAsBatchSeq() {
        assertThat(Json.tree(Json.write(BatchFactory.aBatch())).has("batchSeq")).isTrue();
    }
}
