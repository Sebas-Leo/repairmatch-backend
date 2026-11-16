package com.repairmatch.repairmatch_backend.exception;
import org.springframework.http.HttpStatus;
public class IdentityProviderException extends ApiException {
    public IdentityProviderException(String message) { super(HttpStatus.BAD_GATEWAY, message); }
}
