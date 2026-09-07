package com.mrecoder.errortime.example.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof that {@code error-time-spring-boot-starter} activates
 * purely through its auto-configuration: this application never imports,
 * scans, or otherwise references the library's classes, yet a thrown
 * {@code ResourceNotFoundException} still comes back as a correctly-shaped
 * {@code ProblemDetail} with the traceId/errorCode contract the library
 * defines. Listener auto-startup and tracing export are disabled so this
 * runs hermetically, without a real broker or collector.
 */
@SpringBootTest(properties = {
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "management.tracing.enabled=false"
})
@AutoConfigureMockMvc
class DemoControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownWidgetReturnsProblemDetailFromLibraryAutoConfiguration() throws Exception {
        
        mockMvc.perform(get("/demo/widgets/42"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.traceId").exists())
            .andExpect(jsonPath("$.detail").value("widget with id '42' was not found"));
    }
}
