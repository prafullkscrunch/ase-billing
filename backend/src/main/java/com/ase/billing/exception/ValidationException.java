package com.ase.billing.exception;

import java.util.List;

/** Business-rule failure. Carries every problem found, not just the first. */
public class ValidationException extends RuntimeException {

    private final List<String> problems;

    public ValidationException(String message, List<String> problems) {
        super(message);
        this.problems = List.copyOf(problems);
    }

    public ValidationException(String message) {
        this(message, List.of());
    }

    public List<String> getProblems() {
        return problems;
    }
}
