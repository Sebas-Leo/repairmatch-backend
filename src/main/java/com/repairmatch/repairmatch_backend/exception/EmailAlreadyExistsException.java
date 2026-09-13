package com.repairmatch.repairmatch_backend.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String message) {

        super(message);
    }
}
