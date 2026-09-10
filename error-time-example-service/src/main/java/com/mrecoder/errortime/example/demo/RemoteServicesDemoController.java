package com.mrecoder.errortime.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mrecoder.errortime.example.demo.constant.SimulatedOutcome;
import com.mrecoder.errortime.example.demo.record.DatabaseRecord;
import com.mrecoder.errortime.example.demo.record.LdapUser;
import com.mrecoder.errortime.example.demo.record.QueueMessage;
import com.mrecoder.errortime.example.demo.record.WeatherForecast;
import com.mrecoder.errortime.example.demo.service.CircuitBreakerDemoService;
import com.mrecoder.errortime.example.demo.service.DatabaseStorageService;
import com.mrecoder.errortime.example.demo.service.LdapService;
import com.mrecoder.errortime.example.demo.service.MessageQueueService;
import com.mrecoder.errortime.example.demo.service.WeatherForecastService;

/**
 * One endpoint per pretend remote dependency, each named after the service
 * it reaches. None of these methods catch anything - every exception the
 * services below throw (including an unrecognized {@code simulate} value)
 * propagates straight to {@code error-time-spring-boot-starter}'s
 * auto-configured {@code GlobalExceptionHandler}, the same way it would for
 * any real consumer of the library.
 *
 * <p>Every endpoint takes an optional {@code ?simulate=} query parameter -
 * {@code success} (default), {@code not-found}, {@code invalid}, or
 * {@code unavailable} apply everywhere; {@code unauthenticated},
 * {@code unauthorized}, {@code conflict}, {@code precondition-failed},
 * {@code rate-limited}, and {@code downstream-timeout} apply only to the
 * services they're a natural fit for (see each service class for which).
 * {@code /circuit-breaker/status} is different again - see
 * {@link CircuitBreakerDemoService}.
 */
@RestController
@RequestMapping("/demo/services")
public class RemoteServicesDemoController {

    private final DatabaseStorageService databaseStorageService;
    private final MessageQueueService messageQueueService;
    private final LdapService ldapService;
    private final WeatherForecastService weatherForecastService;
    private final CircuitBreakerDemoService circuitBreakerDemoService;

    public RemoteServicesDemoController(
        DatabaseStorageService databaseStorageService,
        MessageQueueService messageQueueService, LdapService ldapService,
        WeatherForecastService weatherForecastService, CircuitBreakerDemoService circuitBreakerDemoService) {

        this.databaseStorageService = databaseStorageService;
        this.messageQueueService = messageQueueService;
        this.ldapService = ldapService;
        this.weatherForecastService = weatherForecastService;
        this.circuitBreakerDemoService = circuitBreakerDemoService;
    }

    @GetMapping("/database/records/{id}")
    public DatabaseRecord getDatabaseRecord(
        @PathVariable String id,
        @RequestParam(name = "simulate", defaultValue = "success") String simulate) {

        return databaseStorageService.fetchRecord(id, SimulatedOutcome.from(simulate));
    }

    @GetMapping("/message-queue/{queueName}/next-message")
    public QueueMessage getNextQueueMessage(
        @PathVariable String queueName,
        @RequestParam(name = "simulate", defaultValue = "success") String simulate) {

        return messageQueueService.receiveNextMessage(queueName, SimulatedOutcome.from(simulate));
    }

    @GetMapping("/ldap/users/{username}")
    public LdapUser getLdapUser(
        @PathVariable String username,
        @RequestParam(name = "simulate", defaultValue = "success") String simulate) {

        return ldapService.lookupUser(username, SimulatedOutcome.from(simulate));
    }

    @GetMapping("/weather/{cityCode}/forecast")
    public WeatherForecast getWeatherForecast(
        @PathVariable String cityCode,
        @RequestParam(name = "simulate", defaultValue = "success") String simulate) {

        return weatherForecastService.getForecast(cityCode, SimulatedOutcome.from(simulate));
    }

    /**
     * Unlike every endpoint above, repeated {@code ?simulate=unavailable}
     * calls here don't just each return a 503 independently - after enough
     * of them in a row, the circuit breaker itself opens and subsequent
     * calls short-circuit with a {@code CallNotPermittedException} instead
     * of ever reaching {@link CircuitBreakerDemoService#checkStatus}. See
     * that class for the mechanics.
     */
    @GetMapping("/circuit-breaker/status")
    public String getCircuitBreakerStatus(
        @RequestParam(name = "simulate", defaultValue = "success") String simulate) {

        return circuitBreakerDemoService.checkStatus(SimulatedOutcome.from(simulate));
    }
}
