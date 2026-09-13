package com.repairmatch.repairmatch_backend.dto;

import com.repairmatch.repairmatch_backend.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor

public class RegisterRequestDto {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String name;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo debe tener un formato válido")
    @Size(max = 254, message = "El correo no puede superar 254 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(
            min = 8,
            max = 72,
            message = "La contraseña debe tener entre 8 y 72 caracteres"
    )
    private String password;

    @NotNull(message = "El rol es obligatorio")
    private Role role;
}
