package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.dto.MatchingRequestResponseDto;
import com.repairmatch.repairmatch_backend.model.*;
import com.repairmatch.repairmatch_backend.model.Request.RequestStatus;
import com.repairmatch.repairmatch_backend.repository.ApplianceTypeRepository;
import com.repairmatch.repairmatch_backend.repository.RequestRepository;
import com.repairmatch.repairmatch_backend.repository.TechnicianRepository;
import com.repairmatch.repairmatch_backend.repository.UserRepository;
import com.repairmatch.repairmatch_backend.service.impl.TechnicianServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TechnicianServiceTest {

    @Mock private TechnicianRepository technicianRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplianceTypeRepository applianceTypeRepository;
    @Mock private RequestRepository requestRepository;

    @InjectMocks
    private TechnicianServiceImpl technicianService;

    private Technician technician;
    private ApplianceType lavadoraType;
    private ApplianceType refrigeradoraType;
    private UUID techId;

    @BeforeEach
    void setUp() {
        techId = UUID.randomUUID();

        User user = new User(techId, "Técnico de Prueba", "tech@repairmatch.com", "passwordHash123", Role.TECHNICIAN);

        lavadoraType = new ApplianceType();
        lavadoraType.setId(1L);
        lavadoraType.setName("Lavadora");

        refrigeradoraType = new ApplianceType();
        refrigeradoraType.setId(2L);
        refrigeradoraType.setName("Refrigeradora");

        technician = new Technician(user);
        technician.setId(techId);
        technician.setLatitude(-12.046374);  // Coordenadas base (Lima)
        technician.setLongitude(-77.042793);
        technician.setMaxRadiusKm(10.0);      // 10 km de radio
        technician.setApplianceTypes(new HashSet<>(Collections.singletonList(lavadoraType)));
    }

    @Test
    @DisplayName("Debe retornar solicitud compatible dentro del radio y tipo atendido")
    void testMatchingRequestCompatible() {
        when(technicianRepository.findByUserEmail("tech@repairmatch.com")).thenReturn(Optional.of(technician));

        Request compatibleRequest = new Request();
        compatibleRequest.setId(100L);
        compatibleRequest.setStatus(RequestStatus.PUBLICADA);
        compatibleRequest.setApplianceType(lavadoraType);
        compatibleRequest.setLatitude(-12.050000); // ~0.8 km de distancia
        compatibleRequest.setLongitude(-77.040000);
        compatibleRequest.setBrand("Samsung");
        compatibleRequest.setModel("EcoBubble");
        compatibleRequest.setOriginalDescription("No centrifuga adecuadamente");

        when(requestRepository.findAll()).thenReturn(Collections.singletonList(compatibleRequest));

        List<MatchingRequestResponseDto> results = technicianService.getMatchingRequests("tech@repairmatch.com");

        assertEquals(1, results.size());
        assertEquals(100L, results.get(0).getRequestId());
        assertTrue(results.get(0).getDistanceKm() <= 10.0);
    }

    @Test
    @DisplayName("Debe descartar solicitud que excede el radio de cobertura del técnico")
    void testMatchingRequestOutsideRadius() {
        when(technicianRepository.findByUserEmail("tech@repairmatch.com")).thenReturn(Optional.of(technician));

        Request farRequest = new Request();
        farRequest.setId(200L);
        farRequest.setStatus(RequestStatus.PUBLICADA);
        farRequest.setApplianceType(lavadoraType);
        farRequest.setLatitude(-12.200000); // ~18 km de distancia (fuera de los 10 km)
        farRequest.setLongitude(-77.000000);
        farRequest.setOriginalDescription("Fuga de agua");

        when(requestRepository.findAll()).thenReturn(Collections.singletonList(farRequest));

        List<MatchingRequestResponseDto> results = technicianService.getMatchingRequests("tech@repairmatch.com");

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Debe descartar solicitud con tipo de electrodoméstico no atendido")
    void testMatchingRequestDifferentApplianceType() {
        when(technicianRepository.findByUserEmail("tech@repairmatch.com")).thenReturn(Optional.of(technician));

        Request diffTypeRequest = new Request();
        diffTypeRequest.setId(300L);
        diffTypeRequest.setStatus(RequestStatus.PUBLICADA);
        diffTypeRequest.setApplianceType(refrigeradoraType); // No atiende refrigeradoras
        diffTypeRequest.setLatitude(-12.047000);
        diffTypeRequest.setLongitude(-77.042000);
        diffTypeRequest.setOriginalDescription("No enfría el congelador");

        when(requestRepository.findAll()).thenReturn(Collections.singletonList(diffTypeRequest));

        List<MatchingRequestResponseDto> results = technicianService.getMatchingRequests("tech@repairmatch.com");

        assertTrue(results.isEmpty());
    }
}