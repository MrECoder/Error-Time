package com.mrecoder.errortime.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
 * {@code unavailable} - selecting which response the pretend remote call
 * returns.
 */
@RestController
@RequestMapping("/demo/services")
public class RemoteServicesDemoController {

    private final DatabaseStorageService databaseStorageService;
    private final MessageQueueService messageQueueService;
    private final LdapService ldapService;
    private final WeatherForecastService weatherForecastService;

    public RemoteServicesDemoController(DatabaseStorageService databaseStorageService,
            MessageQueueService messageQueueService, LdapService ldapService,
            WeatherForecastService weatherForecastService) {
        this.databaseStorageService = databaseStorageService;
        this.messageQueueService = messageQueueService;
        this.ldapService = ldapService;
        this.weatherForecastService = weatherForecastService;
    }

    @GetMapping("/database/records/{id}")
    public DatabaseRecord getDatabaseRecord(@PathVariable String id,
            @RequestParam(name = "simulate", defaultValue = "success") String simulate) {
        return databaseStorageService.fetchRecord(id, SimulatedOutcome.from(simulate));
    }

    @GetMapping("/message-queue/{queueName}/next-message")
    public QueueMessage getNextQueueMessage(@PathVariable String queueName,
            @RequestParam(name = "simulate", defaultValue = "success") String simulate) {
        return messageQueueService.receiveNextMessage(queueName, SimulatedOutcome.from(simulate));
    }

    @GetMapping("/ldap/users/{username}")
    public LdapUser getLdapUser(@PathVariable String username,
            @RequestParam(name = "simulate", defaultValue = "success") String simulate) {
        return ldapService.lookupUser(username, SimulatedOutcome.from(simulate));
    }

    @GetMapping("/weather/{cityCode}/forecast")
    public WeatherForecast getWeatherForecast(@PathVariable String cityCode,
            @RequestParam(name = "simulate", defaultValue = "success") String simulate) {
        return weatherForecastService.getForecast(cityCode, SimulatedOutcome.from(simulate));
    }
}
