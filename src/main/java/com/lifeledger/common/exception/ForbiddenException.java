package com.lifeledger.common.exception;

/**
 * The user is logged in but may not do this, e.g. not the product owner (HTTP 403).
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
