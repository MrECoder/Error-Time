package com.mrecoder.errortime.example.demo.record;

/** Fake forecast returned by {@link com.mrecoder.errortime.example.demo.service.WeatherForecastService}. */
public record WeatherForecast(String cityCode, String summary, double temperatureCelsius) {
}
