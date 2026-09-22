package com.repairmatch.repairmatch_backend.exception;
import org.springframework.http.HttpStatus;
public class InvalidRefreshTokenException extends ApiException {
    public InvalidRefreshTokenException() { super(HttpStatus.UNAUTHORIZED, "Token de renovación inválido o vencido"); }
}
