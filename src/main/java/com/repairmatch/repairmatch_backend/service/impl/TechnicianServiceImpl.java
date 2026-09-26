package com.repairmatch.repairmatch_backend.service.impl;

import com.repairmatch.repairmatch_backend.dto.*;
import com.repairmatch.repairmatch_backend.exception.IncompleteProfileException;
import com.repairmatch.repairmatch_backend.exception.ResourceNotFoundException;
import com.repairmatch.repairmatch_backend.exception.TechnicianNotFoundException;
import com.repairmatch.repairmatch_backend.model.ApplianceType;
import com.repairmatch.repairmatch_backend.model.Request;
import com.repairmatch.repairmatch_backend.model.Request.RequestStatus;
import com.repairmatch.repairmatch_backend.model.Role;
import com.repairmatch.repairmatch_backend.model.Technician;
import com.repairmatch.repairmatch_backend.model.User;
import com.repairmatch.repairmatch_backend.repository.ApplianceTypeRepository;
import com.repairmatch.repairmatch_backend.repository.RequestRepository;
import com.repairmatch.repairmatch_backend.repository.TechnicianRepository;
import com.repairmatch.repairmatch_backend.repository.UserRepository;
import com.repairmatch.repairmatch_backend.service.TechnicianService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TechnicianServiceImpl implements TechnicianService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final TechnicianRepository technicianRepository;
    private final UserRepository userRepository;
    private final ApplianceTypeRepository applianceTypeRepository;
    private final RequestRepository requestRepository;

    public TechnicianServiceImpl(TechnicianRepository technicianRepository,
                                 UserRepository userRepository,
                                 ApplianceTypeRepository applianceTypeRepository,
                                 RequestRepository requestRepository) {
        this.technicianRepository = technicianRepository;
        this.userRepository = userRepository;
        this.applianceTypeRepository = applianceTypeRepository;
        this.requestRepository = requestRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public TechnicianProfileResponseDto getPublicProfile(UUID technicianId) {
        Technician technician = technicianRepository.findByIdWithApplianceTypes(technicianId)
                .orElseThrow(() -> new TechnicianNotFoundException("Técnico no encontrado con ID: " + technicianId));

        TechnicianProfileResponseDto profile = mapToProfileDto(technician);
        profile.setEmail(null); profile.setPhone(null); profile.setLatitude(null); profile.setLongitude(null);
        return profile;
    }

    @Override
    @Transactional
    public TechnicianProfileResponseDto updateProfile(UUID userId, TechnicianProfileUpdateRequestDto dto) {
        Technician technician = getOrCreateTechnician(userId);

        if (dto.getExperienceYears() != null) {
            technician.setExperienceYears(dto.getExperienceYears());
        }
        if (dto.getBio() != null) {
            technician.setBio(dto.getBio());
        }
        if (dto.getPhone() != null) {
            technician.setPhone(dto.getPhone());
        }

        if (dto.getApplianceTypeIds() != null) {
            Set<ApplianceType> types = new HashSet<>(applianceTypeRepository.findAllById(dto.getApplianceTypeIds()));
            if (types.size() != new HashSet<>(dto.getApplianceTypeIds()).size()) throw new ResourceNotFoundException("Unknown appliance type");
            technician.setApplianceTypes(types);
        }

        technician = technicianRepository.save(technician);
        return mapToProfileDto(technician);
    }

    @Override
    @Transactional
    public TechnicianProfileResponseDto updateServiceArea(UUID userId, ServiceAreaUpdateRequestDto dto) {
        Technician technician = getOrCreateTechnician(userId);

        technician.setLatitude(dto.getLatitude());
        technician.setLongitude(dto.getLongitude());
        technician.setMaxRadiusKm(dto.getMaxRadiusKm());

        technician = technicianRepository.save(technician);
        return mapToProfileDto(technician);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchingRequestResponseDto> getMatchingRequests(UUID userId) {
        Technician technician = technicianRepository.findById(userId)
                .orElseThrow(() -> new TechnicianNotFoundException("Perfil de técnico no configurado"));

        if (technician.getLatitude() == null || technician.getLongitude() == null || technician.getMaxRadiusKm() == null) {
            throw new IncompleteProfileException("Debe configurar su ubicación y radio de servicio antes de buscar solicitudes compatibles");
        }

        Set<Long> supportedTypeIds = technician.getApplianceTypes().stream()
                .map(ApplianceType::getId)
                .collect(Collectors.toSet());

        if (supportedTypeIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Request> allRequests = requestRepository.findAll();
        List<MatchingRequestResponseDto> compatible = new ArrayList<>();

        for (Request req : allRequests) {
            if (new com.repairmatch.repairmatch_backend.service.CompatibilityPolicy().matches(technician, req)) {
                double distance = calculateDistanceKm(technician.getLatitude(), technician.getLongitude(), req.getLatitude(), req.getLongitude());
                MatchingRequestResponseDto item = new MatchingRequestResponseDto();
                item.setRequestId(req.getId());
                item.setApplianceTypeName(req.getApplianceType().getName());
                item.setBrand(req.getBrand());
                item.setModel(req.getModel());
                item.setDescription(req.getOriginalDescription());
                item.setStatus(req.getStatus().name());
                item.setDistanceKm(Math.round(distance * 100.0) / 100.0);
                compatible.add(item);
            }
        }

        compatible.sort(Comparator.comparingDouble(MatchingRequestResponseDto::getDistanceKm));
        return compatible;
    }

    @Override
    public double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        return com.repairmatch.repairmatch_backend.service.CompatibilityPolicy.distanceKm(lat1, lon1, lat2, lon2);
    }

    private Technician getOrCreateTechnician(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + userId));

        if (user.getRole() != Role.TECHNICIAN) {
            throw new IncompleteProfileException("El usuario autenticado no posee rol de técnico");
        }

        return technicianRepository.findById(user.getId())
                .orElseGet(() -> {
                    Technician newTech = new Technician(user);
                    return technicianRepository.save(newTech);
                });
    }

    private TechnicianProfileResponseDto mapToProfileDto(Technician tech) {
        TechnicianProfileResponseDto dto = new TechnicianProfileResponseDto();
        dto.setId(tech.getId());
        dto.setFullName(tech.getUser() != null ? tech.getUser().getName() : null);
        dto.setEmail(tech.getUser() != null ? tech.getUser().getEmail() : null);
        dto.setExperienceYears(tech.getExperienceYears());
        dto.setBio(tech.getBio());
        dto.setPhone(tech.getPhone());
        dto.setLatitude(tech.getLatitude());
        dto.setLongitude(tech.getLongitude());
        dto.setMaxRadiusKm(tech.getMaxRadiusKm());

        if (tech.getApplianceTypes() != null) {
            dto.setApplianceTypes(tech.getApplianceTypes().stream()
                    .map(ApplianceType::getName)
                    .collect(Collectors.toList()));
        } else {
            dto.setApplianceTypes(Collections.emptyList());
        }
        return dto;
    }
}