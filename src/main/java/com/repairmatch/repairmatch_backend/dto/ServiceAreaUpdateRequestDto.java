package com.repairmatch.repairmatch_backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public class ServiceAreaUpdateRequestDto {

    @NotNull(message = "La latitud es obligatoria")
    @DecimalMin(value = "-90.0", message = "La latitud mínima es -90.0")
    @DecimalMax(value = "90.0", message = "La latitud máxima es 90.0")
    private Double latitude;

    @NotNull(message = "La longitud es obligatoria")
    @DecimalMin(value = "-180.0", message = "La longitud mínima es -180.0")
    @DecimalMax(value = "180.0", message = "La longitud máxima es 180.0")
    private Double longitude;

    @NotNull(message = "El radio de cobertura es obligatorio")
    @DecimalMin(value = "1.0", message = "El radio mínimo es 1.0 km")
    @DecimalMax(value = "300.0", message = "El radio máximo es 300.0 km")
    private Double maxRadiusKm;

    public ServiceAreaUpdateRequestDto() {}

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getMaxRadiusKm() { return maxRadiusKm; }
    public void setMaxRadiusKm(Double maxRadiusKm) { this.maxRadiusKm = maxRadiusKm; }
}