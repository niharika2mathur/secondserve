package com.secondserve.exception;

/** 401: missing, invalid or expired session token, or wrong credentials. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) { super(message); }
}
