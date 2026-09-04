package com.mrecoder.errortime.example.web;

import com.mrecoder.errortime.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal endpoint demonstrating that {@code error-time-spring-boot-starter}'s
 * {@code GlobalExceptionHandler} activates purely through its auto-configuration
 * - this application never imports, scans, or otherwise references it.
 */
@RestController
public class DemoController {

    @GetMapping("/demo/widgets/{id}")
    public String getWidget(@PathVariable String id) {
        throw ResourceNotFoundException.of("widget", id);
    }
}
