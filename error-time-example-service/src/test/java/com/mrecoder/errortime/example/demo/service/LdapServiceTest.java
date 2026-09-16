package com.mrecoder.errortime.example.demo.service;

import com.mrecoder.errortime.example.demo.RemoteServiceUnavailableException;
import com.mrecoder.errortime.example.demo.constant.SimulatedOutcome;
import com.mrecoder.errortime.example.demo.record.LdapUser;
import com.mrecoder.errortime.exception.AuthenticationException;
import com.mrecoder.errortime.exception.AuthorizationException;
import com.mrecoder.errortime.exception.ResourceNotFoundException;
import com.mrecoder.errortime.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LdapServiceTest {

    private final LdapService service = new LdapService();

    @Test
    void successReturnsAUserWithTheGivenUsername() {
        LdapUser user = service.lookupUser("jdoe", SimulatedOutcome.SUCCESS);

        assertThat(user.username()).isEqualTo("jdoe");
    }

    @Test
    void notFoundThrowsResourceNotFoundException() {
        assertThatThrownBy(() -> service.lookupUser("jdoe", SimulatedOutcome.NOT_FOUND))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void invalidThrowsValidationException() {
        assertThatThrownBy(() -> service.lookupUser("jdoe", SimulatedOutcome.INVALID))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void unavailableThrowsRemoteServiceUnavailableException() {
        assertThatThrownBy(() -> service.lookupUser("jdoe", SimulatedOutcome.UNAVAILABLE))
            .isInstanceOf(RemoteServiceUnavailableException.class);
    }

    @Test
    void unauthenticatedThrowsAuthenticationException() {
        assertThatThrownBy(() -> service.lookupUser("jdoe", SimulatedOutcome.UNAUTHENTICATED))
            .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void unauthorizedThrowsAuthorizationException() {
        assertThatThrownBy(() -> service.lookupUser("jdoe", SimulatedOutcome.UNAUTHORIZED))
            .isInstanceOf(AuthorizationException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SimulatedOutcome.class,
        names = {"CONFLICT", "PRECONDITION_FAILED", "RATE_LIMITED", "DOWNSTREAM_TIMEOUT"})
    void outcomesThisServiceDoesNotModelAreRejectedAsValidationErrors(SimulatedOutcome outcome) {
        assertThatThrownBy(() -> service.lookupUser("jdoe", outcome))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("is not modeled for");
    }
}
