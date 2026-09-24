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

        return mapToProfileDto(technician);
    }

    @Override
    @Transactional
    public TechnicianProfileResponseDto updateProfile(String userEmail, TechnicianProfileUpdateRequestDto dto) {
        Technician technician = getOrCreateTechnician(userEmail);

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
            technician.setApplianceTypes(types);
        }

        technician = technicianRepository.save(technician);
        return mapToProfileDto(technician);
    }

    @Override
    @Transactional
    public TechnicianProfileResponseDto updateServiceArea(String userEmail, ServiceAreaUpdateRequestDto dto) {
        Technician technician = getOrCreateTechnician(userEmail);

        technician.setLatitude(dto.getLatitude());
        technician.setLongitude(dto.getLongitude());
        technician.setMaxRadiusKm(dto.getMaxRadiusKm());

        technician = technicianRepository.save(technician);
        return mapToProfileDto(technician);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchingRequestResponseDto> getMatchingRequests(String userEmail) {
        Technician technician = technicianRepository.findByUserEmail(userEmail)
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
            // Filtrar únicamente solicitudes abiertas
            if (req.getStatus() != RequestStatus.PUBLICADA && req.getStatus() != RequestStatus.CON_PROPUESTAS) {
                continue;
            }

            // Validar compatibilidad de tipo de electrodoméstico
            if (req.getApplianceType() == null || !supportedTypeIds.contains(req.getApplianceType().getId())) {
                continue;
            }

            // Validar coordenadas de la solicitud
            if (req.getLatitude() == null || req.getLongitude() == null) {
                continue;
            }

            // Calcular distancia mediante Haversine
            double distance = calculateDistanceKm(
                    technician.getLatitude(), technician.getLongitude(),
                    req.getLatitude(), req.getLongitude()
            );

            // Filtrar dentro del radio de cobertura
            if (distance <= technician.getMaxRadiusKm()) {
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
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double originLat = Math.toRadians(lat1);
        double destLat = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(originLat) * Math.cos(destLat)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private Technician getOrCreateTechnician(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));

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