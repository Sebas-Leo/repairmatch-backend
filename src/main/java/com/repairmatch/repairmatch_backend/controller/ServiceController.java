package com.repairmatch.repairmatch_backend.controller;

import com.repairmatch.repairmatch_backend.dto.ReviewRequestDTO;
import com.repairmatch.repairmatch_backend.dto.ServiceResponseDTO;
import com.repairmatch.repairmatch_backend.model.ServiceEntity;
import com.repairmatch.repairmatch_backend.repository.ServiceRepository;
import com.repairmatch.repairmatch_backend.service.ServiceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceService serviceService;
    private final ServiceRepository serviceRepository;

    public ServiceController(ServiceService serviceService, ServiceRepository serviceRepository) {
        this.serviceService = serviceService;
        this.serviceRepository = serviceRepository;
    }

    @GetMapping("/seed")
    public ResponseEntity<String> crearServicioDePrueba() {
        try {
            List<ServiceEntity> existingServices = serviceRepository.findAll();
            if (!existingServices.isEmpty()) {
                ServiceEntity existing = existingServices.get(0);
                return ResponseEntity.ok("El servicio ya existe. Usa este ID: " + existing.getId());
            }

            ServiceEntity s = new ServiceEntity();
            s.setProposalId(1L);
            s.setStatus(ServiceEntity.ServiceStatus.COMPLETADO);
            ServiceEntity saved = serviceRepository.save(s);
            return ResponseEntity.ok("¡Listo! Creado servicio de prueba con ID: " + saved.getId());
        } catch (Exception e) {
            List<ServiceEntity> fallbackList = serviceRepository.findAll();
            if (!fallbackList.isEmpty()) {
                return ResponseEntity.ok("Recuperado por seguridad. Usa este ID: " + fallbackList.get(0).getId());
            }
            return ResponseEntity.status(500).body("Error crítico en seed: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceResponseDTO> getServiceById(@PathVariable Long id) {
        ServiceResponseDTO response = serviceService.getServiceById(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ServiceResponseDTO> updateServiceStatus(
            @PathVariable Long id,
            @RequestParam ServiceEntity.ServiceStatus status) {
        ServiceResponseDTO response = serviceService.updateServiceStatus(id, status);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<?> createReview(
            @PathVariable Long id,
            @RequestParam Long clientId,
            @RequestBody ReviewRequestDTO reviewDTO) {
        try {
            serviceService.createReview(id, clientId, reviewDTO);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error real: " + e.getMessage());
        }
    }
}