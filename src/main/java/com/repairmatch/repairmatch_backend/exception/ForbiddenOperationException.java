package com.repairmatch.repairmatch_backend.exception;
import org.springframework.http.HttpStatus;
public class ForbiddenOperationException extends ApiException {
    public ForbiddenOperationException(String message) { super(HttpStatus.FORBIDDEN, message); }
}
