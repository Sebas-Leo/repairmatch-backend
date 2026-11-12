package com.repairmatch.repairmatch_backend.ai;

import com.repairmatch.repairmatch_backend.dto.PageResponse;
import com.repairmatch.repairmatch_backend.model.ApplianceType;
import com.repairmatch.repairmatch_backend.model.Request;
import com.repairmatch.repairmatch_backend.model.Role;
import com.repairmatch.repairmatch_backend.repository.ApplianceTypeRepository;
import com.repairmatch.repairmatch_backend.security.AccountAccess;
import com.repairmatch.repairmatch_backend.service.RequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Use cases of the local AI: structure a fault description and run a
 * natural-language search over the client's own requests. The models only
 * suggest; the client confirms before anything is saved.
 */
@Service
@RequiredArgsConstructor
public class AiService {
    private static final Map<Request.RequestStatus, String> STATUS_LABEL = Map.of(
            Request.RequestStatus.PUBLICADA, "Publicada",
            Request.RequestStatus.CON_PROPUESTAS, "Con propuestas",
            Request.RequestStatus.CERRADA, "Cerrada",
            Request.RequestStatus.CANCELADA, "Cancelada",
            Request.RequestStatus.EXPIRADA, "Expirada");

    private final FaultAnalyzer analyzer;
    private final SearchInterpreter interpreter;
    private final ApplianceTypeRepository applianceTypes;
    private final RequestService requests;
    private final AccountAccess accountAccess;

    @PreAuthorize("hasRole('CLIENT')")
    @Transactional(readOnly = true)
    public AiDtos.StructuredFault structure(String description) {
        accountAccess.requireRole(Role.CLIENT);
        FaultAnalyzer.FaultAnalysis analysis = analyzer.analyze(description);

        AiDtos.Field appliance = analysis.appliance()
                .map(g -> field(g.value().name(), g.value().label(), g.confidence()))
                .orElse(null);
        Long applianceTypeId = analysis.appliance()
                .filter(g -> g.confidence() >= FaultAnalyzer.MIN_CONFIDENCE)
                .flatMap(g -> catalogId(g.value()))
                .orElse(null);
        AiDtos.Field symptom = analysis.symptom()
                .map(g -> field(g.value().name(), g.value().label(), g.confidence()))
                .orElse(null);
        return new AiDtos.StructuredFault(
                applianceTypeId,
                appliance,
                symptom,
                analysis.brand().orElse(null),
                analysis.model().orElse(null),
                description,
                AiDtos.SOURCE);
    }

    @PreAuthorize("hasRole('CLIENT')")
    @Transactional(readOnly = true)
    public AiDtos.SearchResult search(String query, int page, int size) {
        SearchInterpreter.Interpretation understood = interpreter.interpret(query);
        if (understood.isEmpty()) {
            // Nothing to filter by: an empty answer, not every request of the client.
            return new AiDtos.SearchResult(query, List.of(),
                    new PageResponse<>(List.of(), page, size, 0, 0, true, true), AiDtos.SOURCE);
        }
        Long applianceTypeId = understood.appliance().flatMap(this::catalogId).orElse(null);
        // Calendar days: "hoy" (0) starts at today's midnight, "ayer" (1) at yesterday's.
        LocalDateTime createdAfter = understood.sinceDays()
                .map(days -> java.time.LocalDate.now().minusDays(days).atStartOfDay())
                .orElse(null);
        RequestService.RequestSearchFilter filter = new RequestService.RequestSearchFilter(
                understood.status().orElse(null),
                applianceTypeId,
                createdAfter,
                understood.symptom().map(Symptom::searchTerms).orElse(List.of()),
                understood.brand().orElse(null),
                understood.keywords());
        PageResponse<com.repairmatch.repairmatch_backend.dto.RequestResponseDto> results = requests.searchOwnRequests(
                filter,
                PageResponse.request(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new AiDtos.SearchResult(query, chips(understood), results, AiDtos.SOURCE);
    }

    private List<AiDtos.Chip> chips(SearchInterpreter.Interpretation understood) {
        List<AiDtos.Chip> chips = new ArrayList<>();
        understood.appliance().ifPresent(a -> chips.add(new AiDtos.Chip("appliance", "Equipo", a.label())));
        understood.symptom().ifPresent(s -> chips.add(new AiDtos.Chip("symptom", "Síntoma", s.label())));
        understood.brand().ifPresent(b -> chips.add(new AiDtos.Chip("brand", "Marca", b)));
        understood.status().ifPresent(s -> chips.add(new AiDtos.Chip("status", "Estado", STATUS_LABEL.get(s))));
        understood.sinceDays().ifPresent(d -> chips.add(new AiDtos.Chip("since", "Desde", sinceLabel(d))));
        for (String keyword : understood.keywords()) chips.add(new AiDtos.Chip("keyword", "Palabra", keyword));
        return chips;
    }

    private static String sinceLabel(int days) {
        if (days <= 0) return "hoy";
        if (days == 1) return "ayer";
        if (days < 31) return "hace " + days + " días";
        return "hace " + Math.round(days / 31.0) + " meses";
    }

    private static AiDtos.Field field(String value, String label, double confidence) {
        double rounded = Math.round(confidence * 100) / 100.0;
        return new AiDtos.Field(value, label, rounded, confidence >= FaultAnalyzer.MIN_CONFIDENCE);
    }

    private Optional<Long> catalogId(ApplianceKind kind) {
        return applianceTypes.findAll().stream()
                .filter(type -> kind.matchesCatalogName(type.getName()))
                .map(ApplianceType::getId)
                .findFirst();
    }
}
