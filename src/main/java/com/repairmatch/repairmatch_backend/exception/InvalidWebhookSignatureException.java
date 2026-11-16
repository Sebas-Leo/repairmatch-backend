package com.repairmatch.repairmatch_backend.exception;
import org.springframework.http.HttpStatus;
public class InvalidWebhookSignatureException extends ApiException {
    public InvalidWebhookSignatureException() { super(HttpStatus.UNAUTHORIZED, "Firma del webhook inválida"); }
}
