package com.mrecoder.errortime.example.demo.service;

import com.mrecoder.errortime.example.demo.RemoteServiceUnavailableException;
import com.mrecoder.errortime.example.demo.constant.SimulatedOutcome;
import com.mrecoder.errortime.example.demo.record.DatabaseRecord;
import com.mrecoder.errortime.exception.ResourceNotFoundException;
import com.mrecoder.errortime.exception.ValidationException;
import org.springframework.stereotype.Service;

/**
 * Stands in for a real database client - no actual storage, just enough
 * branching on {@link SimulatedOutcome} to exercise every path through
 * {@code GlobalExceptionHandler}.
 */
@Service
public class DatabaseStorageService {

    public DatabaseRecord fetchRecord(String id, SimulatedOutcome outcome) {

        return switch (outcome) {
            case SUCCESS -> new DatabaseRecord(id, "value-for-" + id);
            case NOT_FOUND -> throw ResourceNotFoundException.of("database record", id);
            case INVALID -> throw new ValidationException("Record id '%s' is not a syntactically valid key".formatted(id));
            case UNAVAILABLE -> throw RemoteServiceUnavailableException.of("database storage service");
        };
    }
}
