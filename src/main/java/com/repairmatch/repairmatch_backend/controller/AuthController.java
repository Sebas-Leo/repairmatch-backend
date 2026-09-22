package com.repairmatch.repairmatch_backend.controller;

import com.repairmatch.repairmatch_backend.dto.RegisterRequestDto;
import com.repairmatch.repairmatch_backend.dto.UserResponseDto;
import com.repairmatch.repairmatch_backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.repairmatch.repairmatch_backend.dto.LoginRequestDto;
import com.repairmatch.repairmatch_backend.dto.LoginResponseDto;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final com.repairmatch.repairmatch_backend.service.RefreshTokenService refreshTokens;

    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> register(
            @Valid @RequestBody RegisterRequestDto request
    ) {
        UserResponseDto response = userService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(
            @Valid @RequestBody LoginRequestDto request
    ) {
        return ResponseEntity.ok(userService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDto> refresh(@Valid @RequestBody com.repairmatch.repairmatch_backend.dto.RefreshRequestDto request) {
        return ResponseEntity.ok(refreshTokens.rotate(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody com.repairmatch.repairmatch_backend.dto.RefreshRequestDto request) {
        refreshTokens.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
