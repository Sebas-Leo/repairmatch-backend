package com.repairmatch.repairmatch_backend.exception;

import org.springframework.http.HttpStatus;

public class TechnicianNotFoundException extends ApiException {
    public TechnicianNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}