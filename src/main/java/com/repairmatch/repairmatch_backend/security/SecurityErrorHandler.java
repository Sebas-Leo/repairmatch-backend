package com.repairmatch.repairmatch_backend.security;
import com.repairmatch.repairmatch_backend.dto.ErrorResponseDto;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
@Component @RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper mapper;
    @Override public void commence(HttpServletRequest request,HttpServletResponse response,AuthenticationException ex) throws IOException {
        response.setHeader("WWW-Authenticate","Bearer");
        write(request,response,HttpStatus.UNAUTHORIZED,"Autenticación requerida");
    }
    @Override public void handle(HttpServletRequest request,HttpServletResponse response,AccessDeniedException ex) throws IOException {
        write(request,response,HttpStatus.FORBIDDEN,"Acceso denegado");
    }
    private void write(HttpServletRequest request,HttpServletResponse response,HttpStatus status,String message) throws IOException {
        response.setStatus(status.value()); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(),ErrorResponseDto.of(status.value(),status.getReasonPhrase(),message,request.getRequestURI(),Map.of()));
    }
}
