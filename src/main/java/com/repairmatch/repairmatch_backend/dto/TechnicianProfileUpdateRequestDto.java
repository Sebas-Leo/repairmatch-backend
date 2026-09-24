package com.repairmatch.repairmatch_backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Set;

public class TechnicianProfileUpdateRequestDto {

    @Min(value = 0, message = "Los años de experiencia no pueden ser negativos")
    @Max(value = 70, message = "Los años de experiencia no pueden superar 70")
    private Integer experienceYears;

    @Size(max = 1000, message = "La biografía no puede superar los 1000 caracteres")
    private String bio;

    @Size(max = 20, message = "El teléfono no puede superar los 20 caracteres")
    private String phone;

    private Set<Long> applianceTypeIds;

    public TechnicianProfileUpdateRequestDto() {}

    public Integer getExperienceYears() { return experienceYears; }
    public void setExperienceYears(Integer experienceYears) { this.experienceYears = experienceYears; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Set<Long> getApplianceTypeIds() { return applianceTypeIds; }
    public void setApplianceTypeIds(Set<Long> applianceTypeIds) { this.applianceTypeIds = applianceTypeIds; }
}