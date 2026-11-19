package com.lifeledger.common.exception;

/**
 * The request is well-formed but breaks a business rule, e.g. a bad serial
 * number or a transfer to yourself (HTTP 400).
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
