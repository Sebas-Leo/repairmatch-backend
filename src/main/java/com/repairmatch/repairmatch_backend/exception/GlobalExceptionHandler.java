package com.repairmatch.repairmatch_backend.exception;

import com.repairmatch.repairmatch_backend.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponseDto> api(ApiException ex, HttpServletRequest request) {
        return response(ex.getStatus(), ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "Revisa los campos enviados", request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> invalidJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "JSON inválido. Verifica los campos y el rol: CLIENT o TECHNICIAN", request, Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponseDto> status(ResponseStatusException ex, HttpServletRequest request) {
        return response(ex.getStatusCode(), ex.getReason() == null ? "Solicitud rechazada" : ex.getReason(), request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> integrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "Los datos incumplen una restricción de integridad", request, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> forbidden(AccessDeniedException ex, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "Acceso denegado", request, Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDto> unauthorized(AuthenticationException ex, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "Autenticación requerida", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> unexpected(Exception ex, HttpServletRequest request) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Error interno de API: {}", ex.getClass().getName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", request, Map.of());
    }

    private ResponseEntity<ErrorResponseDto> response(HttpStatusCode status, String message,
            HttpServletRequest request, Map<String, String> errors) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return ResponseEntity.status(status).body(ErrorResponseDto.of(status.value(),
                known == null ? "Error" : known.getReasonPhrase(), message, request.getRequestURI(), errors));
    }
}
