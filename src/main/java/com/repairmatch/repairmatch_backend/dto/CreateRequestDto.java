package com.repairmatch.repairmatch_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateRequestDto {

    @NotNull(message = "El tipo de electrodoméstico es obligatorio")
    private Long applianceTypeId;

    @NotBlank(message = "La descripción de la falla es obligatoria")
    private String originalDescription;

    private String brand;
    private String model;
}