package com.mrecoder.errortime.example.demo;

/** Fake forecast returned by {@link WeatherForecastService}. */
public record WeatherForecast(String cityCode, String summary, double temperatureCelsius) {
}
