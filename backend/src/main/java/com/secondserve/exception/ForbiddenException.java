package com.secondserve.exception;

/** 403: authenticated, but the role (or record ownership) does not allow this action. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}
