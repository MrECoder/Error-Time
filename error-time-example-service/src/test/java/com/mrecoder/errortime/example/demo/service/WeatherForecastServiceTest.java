package com.mrecoder.errortime.example.demo.service;

import com.mrecoder.errortime.example.demo.RemoteServiceUnavailableException;
import com.mrecoder.errortime.example.demo.constant.SimulatedOutcome;
import com.mrecoder.errortime.example.demo.record.WeatherForecast;
import com.mrecoder.errortime.exception.DownstreamTimeoutException;
import com.mrecoder.errortime.exception.ResourceNotFoundException;
import com.mrecoder.errortime.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeatherForecastServiceTest {

    private final WeatherForecastService service = new WeatherForecastService();

    @Test
    void successReturnsAForecastForTheGivenCityCode() {
        WeatherForecast forecast = service.getForecast("LHR", SimulatedOutcome.SUCCESS);

        assertThat(forecast.cityCode()).isEqualTo("LHR");
    }

    @Test
    void notFoundThrowsResourceNotFoundException() {
        assertThatThrownBy(() -> service.getForecast("LHR", SimulatedOutcome.NOT_FOUND))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void invalidThrowsValidationException() {
        assertThatThrownBy(() -> service.getForecast("LHR", SimulatedOutcome.INVALID))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void unavailableThrowsRemoteServiceUnavailableException() {
        assertThatThrownBy(() -> service.getForecast("LHR", SimulatedOutcome.UNAVAILABLE))
            .isInstanceOf(RemoteServiceUnavailableException.class);
    }

    @Test
    void downstreamTimeoutThrowsDownstreamTimeoutException() {
        assertThatThrownBy(() -> service.getForecast("LHR", SimulatedOutcome.DOWNSTREAM_TIMEOUT))
            .isInstanceOf(DownstreamTimeoutException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SimulatedOutcome.class,
        names = {"UNAUTHENTICATED", "UNAUTHORIZED", "CONFLICT", "PRECONDITION_FAILED", "RATE_LIMITED"})
    void outcomesThisServiceDoesNotModelAreRejectedAsValidationErrors(SimulatedOutcome outcome) {
        assertThatThrownBy(() -> service.getForecast("LHR", outcome))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("is not modeled for");
    }
}
