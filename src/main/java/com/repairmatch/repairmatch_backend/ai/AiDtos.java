package com.repairmatch.repairmatch_backend.ai;

import com.repairmatch.repairmatch_backend.dto.PageResponse;
import com.repairmatch.repairmatch_backend.dto.RequestResponseDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request and response bodies of {@code /api/ai}. */
public final class AiDtos {
    /** Where the interpretation came from; always the in-process model, no external service. */
    public static final String SOURCE = "local-ml";

    private AiDtos() {
    }

    public record StructureRequest(
            @NotBlank(message = "La descripción es obligatoria")
            @Size(max = 5000, message = "La descripción no puede superar los 5000 caracteres")
            String description) {
    }

    /** One understood field: its value, how sure the model is (0-1) and whether it is shown as a suggestion. */
    public record Field(String value, String label, double confidence, boolean confident) {
    }

    /**
     * What the model understood from a fault description. Nothing here is a
     * diagnosis; the client confirms or edits every field before publishing,
     * and {@code originalDescription} is kept exactly as written.
     */
    public record StructuredFault(
            Long applianceTypeId,
            Field appliance,
            Field symptom,
            String brand,
            String model,
            String originalDescription,
            String source) {
    }

    public record SearchRequest(
            @NotBlank(message = "Escribe qué buscas")
            @Size(max = 300, message = "La búsqueda no puede superar los 300 caracteres")
            String query,
            @Min(value = 0, message = "page must be >= 0") Integer page,
            @Min(value = 1, message = "size must be between 1 and 100")
            @Max(value = 100, message = "size must be between 1 and 100") Integer size) {
    }

    /** A filter the search understood, shown to the client as a removable chip. */
    public record Chip(String key, String label, String value) {
    }

    public record SearchResult(
            String query,
            List<Chip> understood,
            PageResponse<RequestResponseDto> results,
            String source) {
    }
}
