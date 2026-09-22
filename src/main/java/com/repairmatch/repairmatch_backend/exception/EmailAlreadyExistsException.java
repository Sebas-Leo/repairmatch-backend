package com.repairmatch.repairmatch_backend.exception;
import org.springframework.http.HttpStatus;
public class EmailAlreadyExistsException extends ApiException {
    public EmailAlreadyExistsException(String message) { super(HttpStatus.CONFLICT, message); }
}
