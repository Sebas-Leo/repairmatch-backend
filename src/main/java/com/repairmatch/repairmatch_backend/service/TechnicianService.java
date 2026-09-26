package com.repairmatch.repairmatch_backend.service;

import com.repairmatch.repairmatch_backend.dto.*;

import java.util.List;
import java.util.UUID;

public interface TechnicianService {
    TechnicianProfileResponseDto getPublicProfile(UUID technicianId);
    TechnicianProfileResponseDto updateProfile(UUID userId, TechnicianProfileUpdateRequestDto dto);
    TechnicianProfileResponseDto updateServiceArea(UUID userId, ServiceAreaUpdateRequestDto dto);
    List<MatchingRequestResponseDto> getMatchingRequests(UUID userId);
    double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2);
}