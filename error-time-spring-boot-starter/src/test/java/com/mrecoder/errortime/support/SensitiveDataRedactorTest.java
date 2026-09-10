package com.mrecoder.errortime.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataRedactorTest {

    private final SensitiveDataRedactor redactor = new SensitiveDataRedactor(true, List.of());

    @Test
    void redactsBuiltInSensitiveFieldNamesCaseInsensitively() {
        assertThat(redactor.isSensitive("password")).isTrue();
        assertThat(redactor.isSensitive("Password")).isTrue();
        assertThat(redactor.isSensitive("userPassword")).isTrue();
        assertThat(redactor.isSensitive("apiKey")).isTrue();
        assertThat(redactor.isSensitive("creditCardNumber")).isTrue();
    }

    @Test
    void leavesOrdinaryFieldNamesAlone() {
        assertThat(redactor.isSensitive("username")).isFalse();
        assertThat(redactor.isSensitive("orderId")).isFalse();
        assertThat(redactor.isSensitive(null)).isFalse();
    }

    @Test
    void redactIfSensitiveReplacesValueOnlyWhenSensitive() {
        assertThat(redactor.redactIfSensitive("password", "hunter2")).isEqualTo(SensitiveDataRedactor.REDACTED);
        assertThat(redactor.redactIfSensitive("username", "bob")).isEqualTo("bob");
    }

    @Test
    void additionalMarkersExtendTheBuiltInList() {
        SensitiveDataRedactor withExtra = new SensitiveDataRedactor(true, List.of("nationalId"));

        assertThat(withExtra.isSensitive("nationalId")).isTrue();
        assertThat(withExtra.isSensitive("password")).isTrue();
    }

    @Test
    void disabledRedactorNeverRedacts() {
        SensitiveDataRedactor disabled = new SensitiveDataRedactor(false, List.of());

        assertThat(disabled.isSensitive("password")).isFalse();
        assertThat(disabled.redactIfSensitive("password", "hunter2")).isEqualTo("hunter2");
    }
}
