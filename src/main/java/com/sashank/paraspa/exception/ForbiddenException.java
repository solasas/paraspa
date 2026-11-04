package com.sashank.paraspa.exception;

/** The caller is known and the resource exists, but they may not do this or see this (HTTP 403). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
