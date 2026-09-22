package com.repairmatch.repairmatch_backend.exception;
import org.springframework.http.HttpStatus;
public class AccountUnavailableException extends ApiException {
    public AccountUnavailableException() { super(HttpStatus.UNAUTHORIZED, "La cuenta autenticada no está disponible"); }
}
