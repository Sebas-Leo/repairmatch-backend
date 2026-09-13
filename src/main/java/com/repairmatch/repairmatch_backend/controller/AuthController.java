package com.repairmatch.repairmatch_backend.controller;

import com.repairmatch.repairmatch_backend.dto.RegisterRequestDto;
import com.repairmatch.repairmatch_backend.dto.UserResponseDto;
import com.repairmatch.repairmatch_backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> register(
            @Valid @RequestBody RegisterRequestDto request
    ) {
        UserResponseDto response = userService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}