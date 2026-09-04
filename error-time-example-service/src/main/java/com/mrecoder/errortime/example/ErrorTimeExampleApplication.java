package com.mrecoder.errortime.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Demo consumer of {@code error-time-spring-boot-starter}. Deliberately lives
 * under a different base package than the library ({@code com.mrecoder.errortime}
 * vs {@code com.mrecoder.errortime.example}) so this behaves exactly like an
 * unrelated microservice would - the library's beans arrive entirely through
 * its auto-configuration, never through component scanning.
 */
@SpringBootApplication
@EnableFeignClients
@EnableRetry
public class ErrorTimeExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErrorTimeExampleApplication.class, args);
    }
}
