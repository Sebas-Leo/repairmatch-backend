package com.repairmatch.repairmatch_backend.dto;
import com.repairmatch.repairmatch_backend.model.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class UserResponseDto {
    private UUID id;
    private String name;
    private String email;
    private Role role;
}
