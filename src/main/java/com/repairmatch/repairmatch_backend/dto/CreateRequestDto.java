package com.repairmatch.repairmatch_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRequestDto {

    @NotNull(message = "El tipo de electrodoméstico es obligatorio")
    private Long applianceTypeId;

    @NotBlank(message = "La descripción de la falla es obligatoria")
    @Size(max = 5000, message = "La descripción no puede superar 5000 caracteres")
    private String originalDescription;

    @Size(max = 100, message = "La marca no puede superar 100 caracteres")
    private String brand;

    @Size(max = 100, message = "El modelo no puede superar 100 caracteres")
    private String model;
}
