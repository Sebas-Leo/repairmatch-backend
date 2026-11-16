package com.repairmatch.repairmatch_backend.exception;
import org.springframework.http.HttpStatus;
public class IdentityNotConfiguredException extends ApiException {
    public IdentityNotConfiguredException(String message) { super(HttpStatus.SERVICE_UNAVAILABLE, message); }
}
