package com.repairmatch.repairmatch_backend.dto;
import java.time.Instant;
import java.util.Map;

public record ErrorResponseDto(Instant timestamp, int status, String error,
        String message, String path, Map<String, String> errors, String detail) {
    public static ErrorResponseDto of(int status, String error, String message, String path,
            Map<String, String> errors) {
        return new ErrorResponseDto(Instant.now(), status, error, message, path, errors, message);
    }
}
