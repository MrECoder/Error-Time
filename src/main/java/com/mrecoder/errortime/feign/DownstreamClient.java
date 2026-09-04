package com.mrecoder.errortime.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Example downstream client for subtasks 5 and 6. Errors are translated by
 * {@link FeignErrorDecoder} (registered globally, see that class) and calls
 * are wrapped with retry in {@link DownstreamService}.
 */
@FeignClient(name = DownstreamClient.CLIENT_NAME, url = "${downstream.service.url:http://localhost:9090}")
public interface DownstreamClient {

    String CLIENT_NAME = "downstream-service";

    @GetMapping("/api/resources/{id}")
    ResourceResponse getResource(@PathVariable("id") String id);
}
