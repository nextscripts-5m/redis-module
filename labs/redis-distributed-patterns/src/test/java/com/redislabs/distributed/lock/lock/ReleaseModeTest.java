package com.redislabs.distributed.lock.lock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseModeTest {

    @Test
    void defaultsToSafe() {
        assertThat(ReleaseMode.parse(null)).isEqualTo(ReleaseMode.SAFE);
        assertThat(ReleaseMode.parse("")).isEqualTo(ReleaseMode.SAFE);
        assertThat(ReleaseMode.parse("safe")).isEqualTo(ReleaseMode.SAFE);
    }

    @Test
    void parsesUnsafe() {
        assertThat(ReleaseMode.parse("unsafe")).isEqualTo(ReleaseMode.UNSAFE);
    }

    @Test
    void rejectsUnknown() {
        assertThatThrownBy(() -> ReleaseMode.parse("bogus"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
