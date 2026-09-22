package com.repairmatch.repairmatch_backend.exception;
import org.springframework.http.HttpStatus;
public class InvalidCredentialsException extends ApiException {
    public InvalidCredentialsException() { super(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"); }
}
