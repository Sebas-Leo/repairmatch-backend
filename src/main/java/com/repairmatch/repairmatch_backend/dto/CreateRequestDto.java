package com.repairmatch.repairmatch_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.AssertTrue;

@Data
public class CreateRequestDto {

    @NotNull(message = "El tipo de electrodoméstico es obligatorio")
    private Long applianceTypeId;

    @DecimalMin("-90") @DecimalMax("90")
    private Double latitude;
    @DecimalMin("-180") @DecimalMax("180")
    private Double longitude;
    @AssertTrue(message = "Latitude and longitude must be supplied together")
    public boolean isLocationComplete() { return (latitude == null) == (longitude == null); }

    @NotBlank(message = "La descripción de la falla es obligatoria")
    @Size(max = 5000, message = "La descripción no puede superar 5000 caracteres")
    private String originalDescription;

    @Size(max = 100, message = "La marca no puede superar 100 caracteres")
    private String brand;

    @Size(max = 100, message = "El modelo no puede superar 100 caracteres")
    private String model;
}
