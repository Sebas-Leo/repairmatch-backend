package com.repairmatch.repairmatch_backend.exception;
import org.springframework.http.HttpStatus;
public class AuthenticationRequiredException extends ApiException {
    public AuthenticationRequiredException(String message) { super(HttpStatus.UNAUTHORIZED, message); }
}
