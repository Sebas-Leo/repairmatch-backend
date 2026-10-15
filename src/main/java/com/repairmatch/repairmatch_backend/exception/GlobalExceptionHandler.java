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
        return response(HttpStatus.BAD_REQUEST, "JSON inválido. Verifica el formato y los valores de los campos (por ejemplo, role: CLIENT o TECHNICIAN)", request, Map.of());
    }

    /** Framework MVC errors (404 unknown path, 405, 415, 406, missing params...) keep their own status instead of falling into the 500 handler. */
    @ExceptionHandler({
            org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotAcceptableException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponseDto> framework(Exception ex, HttpServletRequest request) {
        HttpStatusCode status = ex instanceof org.springframework.web.ErrorResponse er ? er.getStatusCode() : HttpStatus.BAD_REQUEST;
        String message = switch (status.value()) {
            case 404 -> "Ruta no encontrada";
            case 405 -> "Método HTTP no permitido para este recurso";
            case 406 -> "Formato de respuesta no soportado";
            case 415 -> "Content-Type no soportado. Usa application/json";
            default -> "Solicitud inválida";
        };
        return response(status, message, request, Map.of());
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

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class, org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponseDto> invalidParameter(Exception ex, HttpServletRequest request) {
        String name = ex instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException m ? m.getName()
                : ((org.springframework.web.bind.MissingServletRequestParameterException) ex).getParameterName();
        String message = "Invalid request parameter: " + name;
        if (ex instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException m
                && m.getRequiredType() != null && m.getRequiredType().isEnum()) {
            message += " (allowed values: " + java.util.Arrays.stream(m.getRequiredType().getEnumConstants())
                    .map(v -> ((Enum<?>) v).name()).collect(java.util.stream.Collectors.joining(", ")) + ")";
        }
        return response(HttpStatus.BAD_REQUEST, message, request, Map.of(name, "Invalid or missing value"));
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
