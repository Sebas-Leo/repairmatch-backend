package com.repairmatch.repairmatch_backend.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ProblemDetail handleEmailAlreadyExists(
            EmailAlreadyExistsException ex
    ) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {

        Map<String, String> errors = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Revisa los campos enviados"
        );

        problem.setProperty("errors", errors);

        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleInvalidJson(HttpMessageNotReadableException ex) {

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "JSON inválido. Verifica los campos y el rol: CLIENT o TECHNICIAN"
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {

        return ProblemDetail.forStatusAndDetail(
                ex.getStatusCode(),
                ex.getReason() == null ? "Solicitud rechazada" : ex.getReason()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integrity(DataIntegrityViolationException ex) {

        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {

            if (cause instanceof ConstraintViolationException violation) {

                String constraint = violation.getConstraintName();

                if (constraint != null
                        && constraint.toLowerCase(Locale.ROOT)
                        .contains("uk_users_email")) {

                    return ProblemDetail.forStatusAndDetail(
                            HttpStatus.CONFLICT,
                            "El correo ya está registrado"
                    );
                }
            }
        }

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Los datos incumplen una restricción de integridad"
        );
    }
}
