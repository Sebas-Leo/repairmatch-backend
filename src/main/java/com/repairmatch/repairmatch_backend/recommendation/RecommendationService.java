package com.repairmatch.repairmatch_backend.recommendation;

import com.repairmatch.repairmatch_backend.ai.FaultAnalyzer;
import com.repairmatch.repairmatch_backend.ai.Symptom;
import com.repairmatch.repairmatch_backend.dto.MatchingRequestResponseDto;
import com.repairmatch.repairmatch_backend.dto.PageResponse;
import com.repairmatch.repairmatch_backend.dto.ProposalResponseDto;
import com.repairmatch.repairmatch_backend.exception.IncompleteProfileException;
import com.repairmatch.repairmatch_backend.exception.ResourceNotFoundException;
import com.repairmatch.repairmatch_backend.exception.TechnicianNotFoundException;
import com.repairmatch.repairmatch_backend.model.Proposal;
import com.repairmatch.repairmatch_backend.model.Request;
import com.repairmatch.repairmatch_backend.model.Role;
import com.repairmatch.repairmatch_backend.model.Technician;
import com.repairmatch.repairmatch_backend.repository.ProposalRepository;
import com.repairmatch.repairmatch_backend.repository.RequestRepository;
import com.repairmatch.repairmatch_backend.repository.ReviewRepository;
import com.repairmatch.repairmatch_backend.repository.TechnicianRepository;
import com.repairmatch.repairmatch_backend.security.AccountAccess;
import com.repairmatch.repairmatch_backend.service.CompatibilityPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads what the rankings need and hands it to the pure rankers. The rankings
 * are transparent algorithms: each result carries its factors and reasons.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final RequestRepository requests;
    private final ProposalRepository proposals;
    private final TechnicianRepository technicians;
    private final ReviewRepository reviews;
    private final CompatibilityPolicy compatibilityPolicy;
    private final FaultAnalyzer analyzer;
    private final AccountAccess accountAccess;

    /** The proposals of the caller's request, best first. */
    @PreAuthorize("hasRole('CLIENT')")
    @Transactional(readOnly = true)
    public List<RecommendationDtos.RecommendedProposal> recommendProposals(Long requestId) {
        UUID client = accountAccess.requireRole(Role.CLIENT);
        Request request = requests.findWithDetailsById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        accountAccess.requireOwner(client, request.getClient().getId());

        List<Proposal> received = proposals.findByRequestIdOrderByDiagnosticCostAscAvailableAtAsc(requestId);
        if (received.isEmpty()) return List.of();
        Set<UUID> technicianIds = received.stream().map(p -> p.getTechnician().getId()).collect(Collectors.toSet());
        Map<UUID, Technician> profiles = technicians.findAllById(technicianIds).stream()
                .collect(Collectors.toMap(Technician::getId, Function.identity()));
        Map<UUID, Object[]> stats = byFirstColumn(reviews.statsForTechnicians(technicianIds));
        Map<UUID, Object[]> ownRatings = byFirstColumn(reviews.ratingsByClient(client, technicianIds));

        Map<Long, Proposal> byId = new HashMap<>();
        List<ProposalRanker.Candidate> candidates = new ArrayList<>();
        for (Proposal p : received) {
            UUID technicianId = p.getTechnician().getId();
            Object[] row = stats.get(technicianId);
            Object[] own = ownRatings.get(technicianId);
            byId.put(p.getId(), p);
            candidates.add(new ProposalRanker.Candidate(
                    p.getId(),
                    p.getDiagnosticCost(),
                    p.getAvailableAt(),
                    distance(profiles.get(technicianId), request).orElse(null),
                    row == null ? 0 : ((Number) row[1]).doubleValue(),
                    row == null ? 0 : ((Number) row[2]).longValue(),
                    own == null ? null : ((Number) own[1]).doubleValue()));
        }

        List<ProposalRanker.Ranked> ranked = ProposalRanker.rank(candidates, LocalDateTime.now());
        List<RecommendationDtos.RecommendedProposal> result = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            ProposalRanker.Ranked r = ranked.get(i);
            ProposalRanker.Candidate c = r.candidate();
            result.add(new RecommendationDtos.RecommendedProposal(
                    ProposalResponseDto.from(byId.get(c.proposalId())),
                    r.score(),
                    i == 0,
                    c.distanceKm() == null ? null : Ranking.round(c.distanceKm()),
                    Ranking.round(c.averageRating()),
                    c.reviewCount(),
                    r.factors(),
                    r.reasons()));
        }
        return result;
    }

    /** Open requests the calling technician can serve and has not answered yet, best first. */
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Transactional(readOnly = true)
    public PageResponse<RecommendationDtos.RecommendedRequest> recommendRequests(PageRequest window) {
        UUID me = accountAccess.requireRole(Role.TECHNICIAN);
        Technician technician = technicians.findByIdWithApplianceTypes(me)
                .orElseThrow(() -> new TechnicianNotFoundException("Perfil de técnico no configurado"));
        if (!compatibilityPolicy.configured(technician)) {
            throw new IncompleteProfileException(
                    "Debe configurar su ubicación y radio de servicio antes de buscar solicitudes compatibles");
        }

        Set<Long> answered = new HashSet<>(proposals.requestIdsProposedBy(me));
        List<Request> open = requests.findAll().stream()
                .filter(r -> !answered.contains(r.getId()))
                .filter(r -> compatibilityPolicy.matches(technician, r))
                .toList();
        if (open.isEmpty()) return PageResponse.slice(List.of(), window);

        Map<Long, Long> proposalCounts = new HashMap<>();
        for (Object[] row : proposals.countByRequestIds(open.stream().map(Request::getId).toList())) {
            proposalCounts.put((Long) row[0], ((Number) row[1]).longValue());
        }

        Map<Long, Request> byId = new HashMap<>();
        Map<Long, Symptom> symptoms = new HashMap<>();
        List<RequestRanker.Candidate> candidates = new ArrayList<>();
        for (Request r : open) {
            Symptom symptom = symptomOf(r).orElse(null);
            byId.put(r.getId(), r);
            if (symptom != null) symptoms.put(r.getId(), symptom);
            candidates.add(new RequestRanker.Candidate(
                    r.getId(),
                    r.getApplianceType().getId(),
                    r.getBrand(),
                    symptom,
                    distance(technician, r).orElse(0.0),
                    r.getCreatedAt(),
                    proposalCounts.getOrDefault(r.getId(), 0L)));
        }

        List<RecommendationDtos.RecommendedRequest> ranked = RequestRanker
                .rank(candidates, history(requests.findWonBy(me)), LocalDateTime.now()).stream()
                .map(r -> {
                    Request request = byId.get(r.candidate().requestId());
                    Symptom symptom = symptoms.get(request.getId());
                    return new RecommendationDtos.RecommendedRequest(
                            matching(request, r.candidate().distanceKm()),
                            request.getCreatedAt(),
                            r.candidate().proposalCount(),
                            symptom == null ? null : symptom.label(),
                            r.score(),
                            r.factors(),
                            r.reasons());
                })
                .toList();
        return PageResponse.slice(ranked, window);
    }

    /** Counts of the won jobs per appliance type, brand and symptom (read by the local AI). */
    RequestRanker.History history(Collection<Request> won) {
        Map<Long, Integer> byType = new HashMap<>();
        Map<String, Integer> byBrand = new HashMap<>();
        Map<Symptom, Integer> bySymptom = new HashMap<>();
        for (Request r : won) {
            if (r.getApplianceType() != null) byType.merge(r.getApplianceType().getId(), 1, Integer::sum);
            String brand = RequestRanker.brandKey(r.getBrand());
            if (!brand.isEmpty()) byBrand.merge(brand, 1, Integer::sum);
            symptomOf(r).ifPresent(s -> bySymptom.merge(s, 1, Integer::sum));
        }
        return new RequestRanker.History(byType, byBrand, bySymptom, won.size());
    }

    private Optional<Symptom> symptomOf(Request r) {
        return analyzer.classifySymptom(r.getOriginalDescription() == null ? "" : r.getOriginalDescription())
                .filter(g -> g.confidence() >= FaultAnalyzer.MIN_CONFIDENCE)
                .map(FaultAnalyzer.Guess::value);
    }

    private static Optional<Double> distance(Technician t, Request r) {
        if (t == null || t.getLatitude() == null || t.getLongitude() == null
                || r.getLatitude() == null || r.getLongitude() == null) return Optional.empty();
        return Optional.of(CompatibilityPolicy.distanceKm(t.getLatitude(), t.getLongitude(), r.getLatitude(), r.getLongitude()));
    }

    private static MatchingRequestResponseDto matching(Request r, double distanceKm) {
        MatchingRequestResponseDto item = new MatchingRequestResponseDto();
        item.setRequestId(r.getId());
        item.setApplianceTypeName(r.getApplianceType().getName());
        item.setBrand(r.getBrand());
        item.setModel(r.getModel());
        item.setDescription(r.getOriginalDescription());
        item.setStatus(r.getStatus().name());
        item.setDistanceKm(Ranking.round(distanceKm));
        return item;
    }

    private static Map<UUID, Object[]> byFirstColumn(List<Object[]> rows) {
        Map<UUID, Object[]> map = new HashMap<>();
        for (Object[] row : rows) map.put((UUID) row[0], row);
        return map;
    }
}
