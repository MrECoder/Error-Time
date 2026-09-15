package com.mrecoder.errortime.example.amqp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * RabbitMQ's own {@code guest} account is restricted to localhost
 * connections by the broker itself, which makes it a reasonable local-dev
 * default - but that restriction lives in the broker, not this application,
 * so a deployment that exposes the broker more broadly (a permissive
 * container network, a misconfigured security group) would silently inherit
 * a well-known credential. This makes that condition loud instead of silent.
 */
@Slf4j
@Component
public class RabbitCredentialsWarner {

    private final String username;

    public RabbitCredentialsWarner(@Value("${spring.rabbitmq.username:guest}") String username) {
        this.username = username;
    }

    @PostConstruct
    void warnIfUsingDefaultCredentials() {
        if ("guest".equals(username)) {
            log.warn("RabbitMQ is configured with the default 'guest' username. RabbitMQ itself restricts "
                + "'guest' to localhost connections, but if that restriction doesn't hold in this environment "
                + "(a shared network, a permissive container/cloud network policy), this is a well-known "
                + "credential. Override RABBITMQ_USERNAME/RABBITMQ_PASSWORD outside local development.");
        }
    }
}
