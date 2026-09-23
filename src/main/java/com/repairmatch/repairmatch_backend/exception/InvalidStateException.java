package com.repairmatch.repairmatch_backend.exception;
import org.springframework.http.HttpStatus;
public class InvalidStateException extends ApiException {
    public InvalidStateException(String message) { super(HttpStatus.CONFLICT, message); }
}
