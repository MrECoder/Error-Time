package com.mrecoder.errortime.example.amqp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class RabbitCredentialsWarnerTest {

    @Test
    void warnsWhenUsernameIsStillTheRabbitMqDefault() {
        assertThatCode(() -> new RabbitCredentialsWarner("guest").warnIfUsingDefaultCredentials())
            .doesNotThrowAnyException();
    }

    @Test
    void staysQuietWhenUsernameHasBeenOverridden() {
        assertThatCode(() -> new RabbitCredentialsWarner("app-service").warnIfUsingDefaultCredentials())
            .doesNotThrowAnyException();
    }
}
