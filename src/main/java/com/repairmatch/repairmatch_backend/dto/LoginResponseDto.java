package com.repairmatch.repairmatch_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponseDto {

    private final String accessToken;
    private final String tokenType;
    private final long expiresIn;
    private final String refreshToken;
    private final long refreshExpiresIn;

    public LoginResponseDto(String accessToken, String tokenType, long expiresIn) {
        this(accessToken, tokenType, expiresIn, null, 0);
    }
}