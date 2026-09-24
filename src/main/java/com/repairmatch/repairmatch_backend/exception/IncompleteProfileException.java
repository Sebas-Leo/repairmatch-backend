package com.repairmatch.repairmatch_backend.exception;

import org.springframework.http.HttpStatus;

public class IncompleteProfileException extends ApiException {
    public IncompleteProfileException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}