package com.mrecoder.errortime.example.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Example downstream Feign client. Errors are translated by the library's
 * {@code com.mrecoder.errortime.feign.FeignErrorDecoder} (auto-registered
 * globally) and calls are wrapped with retry in {@link DownstreamService}.
 */
@FeignClient(name = DownstreamClient.CLIENT_NAME, url = "${downstream.service.url:http://localhost:9090}")
public interface DownstreamClient {

    String CLIENT_NAME = "downstream-service";

    @GetMapping("/api/resources/{id}")
    ResourceResponse getResource(@PathVariable("id") String id);
}
