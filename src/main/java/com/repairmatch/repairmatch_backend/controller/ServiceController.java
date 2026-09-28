package com.repairmatch.repairmatch_backend.controller;
import com.repairmatch.repairmatch_backend.dto.*;
import com.repairmatch.repairmatch_backend.model.ServiceEntity;
import com.repairmatch.repairmatch_backend.service.ServiceService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/services") @RequiredArgsConstructor
public class ServiceController {
    private final ServiceService serviceService;
    @GetMapping public List<ServiceResponseDTO> getOwnServices() { return serviceService.getOwnServices(); }
    @GetMapping("/{id}") public ServiceResponseDTO getServiceById(@PathVariable Long id) { return serviceService.getServiceById(id); }
    @PatchMapping("/{id}/status") public ServiceResponseDTO updateServiceStatus(@PathVariable Long id, @RequestParam ServiceEntity.ServiceStatus status) {
        return serviceService.updateServiceStatus(id, status);
    }
    @PostMapping("/{id}/review") @ResponseStatus(HttpStatus.CREATED)
    public void createReview(@PathVariable Long id, @Valid @RequestBody ReviewRequestDTO dto) { serviceService.createReview(id, dto); }
}
