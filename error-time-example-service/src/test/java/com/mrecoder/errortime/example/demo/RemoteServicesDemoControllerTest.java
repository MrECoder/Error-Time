package com.mrecoder.errortime.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises every pretend remote service through {@link RemoteServicesDemoController},
 * proving {@code error-time-spring-boot-starter}'s auto-configured
 * {@code GlobalExceptionHandler} maps each outcome correctly - including the
 * {@code SERVICE_UNAVAILABLE} code this application defines itself via the
 * library's {@code ErrorCode} interface (see {@link DemoErrorCode}).
 */
@SpringBootTest(properties = {
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "management.tracing.enabled=false"
})
@AutoConfigureMockMvc
class RemoteServicesDemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void databaseRecordSucceedsByDefault() throws Exception {
        mockMvc.perform(get("/demo/services/database/records/42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("42"));
    }

    @Test
    void databaseRecordNotFound() throws Exception {
        mockMvc.perform(get("/demo/services/database/records/42").param("simulate", "not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void databaseRecordUnavailable() throws Exception {
        mockMvc.perform(get("/demo/services/database/records/42").param("simulate", "unavailable"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void messageQueueSucceedsByDefault() throws Exception {
        mockMvc.perform(get("/demo/services/message-queue/orders/next-message"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queueName").value("orders"));
    }

    @Test
    void messageQueueUnavailable() throws Exception {
        mockMvc.perform(get("/demo/services/message-queue/orders/next-message").param("simulate", "unavailable"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void ldapUserSucceedsByDefault() throws Exception {
        mockMvc.perform(get("/demo/services/ldap/users/jdoe"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("jdoe"));
    }

    @Test
    void ldapUserInvalid() throws Exception {
        mockMvc.perform(get("/demo/services/ldap/users/jdoe").param("simulate", "invalid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void ldapUserUnavailable() throws Exception {
        mockMvc.perform(get("/demo/services/ldap/users/jdoe").param("simulate", "unavailable"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void weatherForecastSucceedsByDefault() throws Exception {
        mockMvc.perform(get("/demo/services/weather/LHR/forecast"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cityCode").value("LHR"));
    }

    @Test
    void weatherForecastUnavailable() throws Exception {
        mockMvc.perform(get("/demo/services/weather/LHR/forecast").param("simulate", "unavailable"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void unknownSimulateValueIsRejectedAsValidationError() throws Exception {
        mockMvc.perform(get("/demo/services/database/records/42").param("simulate", "bogus"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
