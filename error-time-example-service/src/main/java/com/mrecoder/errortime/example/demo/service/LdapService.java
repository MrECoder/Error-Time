package com.mrecoder.errortime.example.demo.service;

import com.mrecoder.errortime.example.demo.RemoteServiceUnavailableException;
import com.mrecoder.errortime.example.demo.constant.SimulatedOutcome;
import com.mrecoder.errortime.example.demo.record.LdapUser;
import com.mrecoder.errortime.exception.ResourceNotFoundException;
import com.mrecoder.errortime.exception.ValidationException;
import org.springframework.stereotype.Service;

/**
 * Stands in for a real LDAP/directory client - no actual bind, just enough
 * branching on {@link SimulatedOutcome} to exercise every path through
 * {@code GlobalExceptionHandler}.
 */
@Service
public class LdapService {

    public LdapUser lookupUser(String username, SimulatedOutcome outcome) {
        
        return switch (outcome) {
            case SUCCESS -> new LdapUser(username, "cn=%s,ou=users,dc=example,dc=com".formatted(username));
            case NOT_FOUND -> throw ResourceNotFoundException.of("LDAP user", username);
            case INVALID -> throw new ValidationException("Username '%s' contains characters not permitted in a distinguished name".formatted(username));
            case UNAVAILABLE -> throw RemoteServiceUnavailableException.of("LDAP directory service");
        };
    }
}
