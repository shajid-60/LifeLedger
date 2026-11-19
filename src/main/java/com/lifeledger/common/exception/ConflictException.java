package com.lifeledger.common.exception;

/**
 * A duplicate or a request that conflicts with the current state (HTTP 409).
 * Subclass it for specific cases, e.g. M3's InvalidStateTransitionException.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
