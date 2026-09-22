package com.repairmatch.repairmatch_backend.exception;
import org.springframework.http.HttpStatus;
public class InvalidPasswordException extends ApiException {
    public InvalidPasswordException() { super(HttpStatus.BAD_REQUEST, "La contraseña no puede superar 72 bytes en UTF-8"); }
}
