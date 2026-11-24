package com.secondserve.exception;

import java.util.List;

public class ValidationException extends RuntimeException {
    private final List<String> errors;
    public ValidationException(String message, List<String> errors) { super(message); this.errors = List.copyOf(errors); }
    public ValidationException(List<String> errors) { this("Validation failed", errors); }
    public ValidationException(String single) { this(single, List.of(single)); }
    public List<String> getErrors() { return errors; }
}
