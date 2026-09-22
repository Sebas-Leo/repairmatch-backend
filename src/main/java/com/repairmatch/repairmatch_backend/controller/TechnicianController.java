package com.repairmatch.repairmatch_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/technicians")
public class TechnicianController {

    @GetMapping("/{id}/reputation")
    public ResponseEntity<?> getTechnicianReputation(@PathVariable long id) {
        return ResponseEntity.ok("Reputación promedio del técnico " + id);
    }
}