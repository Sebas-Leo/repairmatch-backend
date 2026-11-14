package com.repairmatch.repairmatch_backend.controller;
import com.repairmatch.repairmatch_backend.repository.ApplianceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/appliance-types") @RequiredArgsConstructor
public class ApplianceTypeController {
    private final ApplianceTypeRepository repository;
    public record ApplianceTypeResponse(Long id, String name, String description) {}
    @GetMapping public List<ApplianceTypeResponse> list() {
        return repository.findAll(org.springframework.data.domain.Sort.by("name")).stream()
            .map(t -> new ApplianceTypeResponse(t.getId(), t.getName(), t.getDescription())).toList();
    }
}
