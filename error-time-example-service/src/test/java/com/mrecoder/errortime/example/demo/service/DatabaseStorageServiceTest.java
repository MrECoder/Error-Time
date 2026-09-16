package com.mrecoder.errortime.example.demo.service;

import com.mrecoder.errortime.example.demo.RemoteServiceUnavailableException;
import com.mrecoder.errortime.example.demo.constant.SimulatedOutcome;
import com.mrecoder.errortime.example.demo.record.DatabaseRecord;
import com.mrecoder.errortime.exception.ConflictException;
import com.mrecoder.errortime.exception.PreconditionFailedException;
import com.mrecoder.errortime.exception.ResourceNotFoundException;
import com.mrecoder.errortime.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct unit coverage of every {@link SimulatedOutcome} branch this service
 * models (or deliberately doesn't) - {@code RemoteServicesDemoControllerTest}
 * only exercises the outcomes reachable through the HTTP layer that it chose
 * to assert on, not every enum value.
 */
class DatabaseStorageServiceTest {

    private final DatabaseStorageService service = new DatabaseStorageService();

    @Test
    void successReturnsARecordWithTheGivenId() {
        DatabaseRecord record = service.fetchRecord("42", SimulatedOutcome.SUCCESS);

        assertThat(record.id()).isEqualTo("42");
    }

    @Test
    void notFoundThrowsResourceNotFoundException() {
        assertThatThrownBy(() -> service.fetchRecord("42", SimulatedOutcome.NOT_FOUND))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void invalidThrowsValidationException() {
        assertThatThrownBy(() -> service.fetchRecord("42", SimulatedOutcome.INVALID))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void unavailableThrowsRemoteServiceUnavailableException() {
        assertThatThrownBy(() -> service.fetchRecord("42", SimulatedOutcome.UNAVAILABLE))
            .isInstanceOf(RemoteServiceUnavailableException.class);
    }

    @Test
    void conflictThrowsConflictException() {
        assertThatThrownBy(() -> service.fetchRecord("42", SimulatedOutcome.CONFLICT))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void preconditionFailedThrowsPreconditionFailedException() {
        assertThatThrownBy(() -> service.fetchRecord("42", SimulatedOutcome.PRECONDITION_FAILED))
            .isInstanceOf(PreconditionFailedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SimulatedOutcome.class,
        names = {"UNAUTHENTICATED", "UNAUTHORIZED", "RATE_LIMITED", "DOWNSTREAM_TIMEOUT"})
    void outcomesThisServiceDoesNotModelAreRejectedAsValidationErrors(SimulatedOutcome outcome) {
        assertThatThrownBy(() -> service.fetchRecord("42", outcome))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("is not modeled for");
    }
}
