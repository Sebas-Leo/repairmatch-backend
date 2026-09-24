package com.repairmatch.repairmatch_backend.dto;

import java.util.List;
import java.util.UUID;

public class TechnicianProfileResponseDto {

    private UUID id;
    private String fullName;
    private String email;
    private Integer experienceYears;
    private String bio;
    private String phone;
    private Double latitude;
    private Double longitude;
    private Double maxRadiusKm;
    private List<String> applianceTypes;

    public TechnicianProfileResponseDto() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Integer getExperienceYears() { return experienceYears; }
    public void setExperienceYears(Integer experienceYears) { this.experienceYears = experienceYears; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getMaxRadiusKm() { return maxRadiusKm; }
    public void setMaxRadiusKm(Double maxRadiusKm) { this.maxRadiusKm = maxRadiusKm; }

    public List<String> getApplianceTypes() { return applianceTypes; }
    public void setApplianceTypes(List<String> applianceTypes) { this.applianceTypes = applianceTypes; }
}