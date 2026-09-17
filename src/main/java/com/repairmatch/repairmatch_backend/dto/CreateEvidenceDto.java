package com.repairmatch.repairmatch_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateEvidenceDto {

    @NotBlank(message = "La URL del archivo es obligatoria")
    @Size(max = 500, message = "La URL no puede exceder los 500 caracteres")
    private String mediaUrl;

    @NotBlank(message = "El tipo de archivo es obligatorio")
    @Size(max = 50, message = "El tipo de contenido no debe exceder los 50 caracteres")
    private String mediaType;
}