package com.repairmatch.repairmatch_backend.dto;

public class MatchingRequestResponseDto {

    private Long requestId;
    private String applianceTypeName;
    private String brand;
    private String model;
    private String description;
    private String status;
    private Double distanceKm;
    /** Snapped to a ~550 m grid (see ApproximateLocation); never the exact point. */
    private Double approxLatitude;
    private Double approxLongitude;

    public MatchingRequestResponseDto() {}

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public String getApplianceTypeName() { return applianceTypeName; }
    public void setApplianceTypeName(String applianceTypeName) { this.applianceTypeName = applianceTypeName; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }

    public Double getApproxLatitude() { return approxLatitude; }
    public void setApproxLatitude(Double approxLatitude) { this.approxLatitude = approxLatitude; }

    public Double getApproxLongitude() { return approxLongitude; }
    public void setApproxLongitude(Double approxLongitude) { this.approxLongitude = approxLongitude; }
}