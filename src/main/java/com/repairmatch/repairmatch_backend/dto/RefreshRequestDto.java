package com.repairmatch.repairmatch_backend.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record RefreshRequestDto(@NotBlank @Size(max=512) String refreshToken) {}
