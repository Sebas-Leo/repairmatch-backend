package com.repairmatch.repairmatch_backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "technicians")
public class Technician {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Min(value = 0, message = "Los años de experiencia no pueden ser negativos")
    @Max(value = 70, message = "Los años de experiencia deben ser coherentes")
    @Column(name = "experience_years")
    private Integer experienceYears;

    @Size(max = 1000, message = "La biografía no puede superar los 1000 caracteres")
    @Column(name = "bio", length = 1000)
    private String bio;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Min(value = 1, message = "El radio mínimo debe ser de 1 km")
    @Max(value = 300, message = "El radio no puede exceder los 300 km")
    @Column(name = "max_radius_km")
    private Double maxRadiusKm;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "technician_appliance_types",
            joinColumns = @JoinColumn(name = "technician_id"),
            inverseJoinColumns = @JoinColumn(name = "appliance_type_id")
    )
    private Set<ApplianceType> applianceTypes = new HashSet<>();

    public Technician() {}

    public Technician(User user) {
        this.user = user;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

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

    public Set<ApplianceType> getApplianceTypes() { return applianceTypes; }
    public void setApplianceTypes(Set<ApplianceType> applianceTypes) { this.applianceTypes = applianceTypes; }
}