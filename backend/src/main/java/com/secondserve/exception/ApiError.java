package com.secondserve.exception;

import java.util.List;

/** Uniform JSON error body returned by every endpoint. */
public record ApiError(int status, String error, String message, List<String> details) {}
