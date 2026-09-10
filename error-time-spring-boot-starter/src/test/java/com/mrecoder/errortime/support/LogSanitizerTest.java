package com.mrecoder.errortime.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void leavesOrdinaryStringsUnchanged() {
        assertThat(LogSanitizer.sanitize("widget-42")).isEqualTo("widget-42");
    }

    @Test
    void returnsNullForNull() {
        assertThat(LogSanitizer.sanitize(null)).isNull();
    }

    @Test
    void stripsCarriageReturnAndLineFeedToPreventLogForging() {
        String attack = "innocuous\r\nWARN forged log line injected by attacker";

        String sanitized = LogSanitizer.sanitize(attack);

        assertThat(sanitized).doesNotContain("\r").doesNotContain("\n");
        assertThat(sanitized).contains("innocuous").contains("forged log line injected by attacker");
    }

    @Test
    void stripsOtherControlCharactersButKeepsTabs() {
        String withControlCharAndTab = "ab\tc";

        String sanitized = LogSanitizer.sanitize(withControlCharAndTab);

        assertThat(sanitized).isEqualTo("a\\nb\tc");
    }
}
