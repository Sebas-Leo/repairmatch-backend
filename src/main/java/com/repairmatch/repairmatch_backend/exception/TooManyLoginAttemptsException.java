package com.repairmatch.repairmatch_backend.exception;

import org.springframework.http.HttpStatus;

public class TooManyLoginAttemptsException extends ApiException {
    public TooManyLoginAttemptsException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos fallidos. Espera 15 minutos e inténtalo de nuevo");
    }
}
