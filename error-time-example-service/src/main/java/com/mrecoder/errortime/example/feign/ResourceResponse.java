package com.mrecoder.errortime.example.feign;

/** Example downstream response DTO for {@link DownstreamClient}. */
public record ResourceResponse(String id, String name) {
}
