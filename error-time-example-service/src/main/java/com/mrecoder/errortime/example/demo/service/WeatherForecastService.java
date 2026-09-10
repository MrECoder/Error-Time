package com.mrecoder.errortime.example.demo.service;

import com.mrecoder.errortime.example.demo.RemoteServiceUnavailableException;
import com.mrecoder.errortime.example.demo.constant.SimulatedOutcome;
import com.mrecoder.errortime.example.demo.record.WeatherForecast;
import com.mrecoder.errortime.exception.DownstreamTimeoutException;
import com.mrecoder.errortime.exception.ResourceNotFoundException;
import com.mrecoder.errortime.exception.ValidationException;
import org.springframework.stereotype.Service;

/**
 * Stands in for a real weather-forecasting API client - no actual call, just
 * enough branching on {@link SimulatedOutcome} to exercise every path
 * through {@code GlobalExceptionHandler}, including the outcome a
 * third-party HTTP API is naturally prone to: not responding in time
 * ({@code downstream-timeout}).
 */
@Service
public class WeatherForecastService {

    public WeatherForecast getForecast(String cityCode, SimulatedOutcome outcome) {

        return switch (outcome) {
            case SUCCESS -> new WeatherForecast(cityCode, "Partly cloudy", 18.5);
            case NOT_FOUND -> throw ResourceNotFoundException.of("weather station", cityCode);
            case INVALID -> throw new ValidationException("City code '%s' is not a recognized IATA/ICAO location code".formatted(cityCode));
            case UNAVAILABLE -> throw RemoteServiceUnavailableException.of("weather forecasting service");
            case DOWNSTREAM_TIMEOUT -> throw new DownstreamTimeoutException(
                "Weather API did not respond in time for city code '%s'".formatted(cityCode));
            case UNAUTHENTICATED, UNAUTHORIZED, CONFLICT, PRECONDITION_FAILED, RATE_LIMITED ->
                throw outcome.unsupportedFor("the weather forecast lookup");
        };
    }
}
