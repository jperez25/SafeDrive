package com.qnt.safedrive.tests.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.utils.Ids;
import org.testng.annotations.Test;

public class IdsTest {

    @Test
    public void idsCarryTheirPrefixAndAreUnique() {
        assertThat(Ids.correlation()).startsWith("it-").isNotEqualTo(Ids.correlation());
        assertThat(Ids.batch()).startsWith("b-");
        assertThat(Ids.trip()).startsWith("t-");
        assertThat(Ids.driver()).startsWith("d-");
        assertThat(Ids.device()).startsWith("dev-");
    }
}
