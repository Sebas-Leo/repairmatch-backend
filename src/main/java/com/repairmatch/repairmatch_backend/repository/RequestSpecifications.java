package com.repairmatch.repairmatch_backend.repository;

import com.repairmatch.repairmatch_backend.model.Request;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/** Filters for the client's own request list. Each one is optional and they combine with AND. */
public final class RequestSpecifications {
    private static final String ACCENTED = "áéíóúüàèìòùâêîôûñ";
    private static final String PLAIN = "aeiouuaeiouaeioun";

    private RequestSpecifications() {
    }

    public static Specification<Request> ownedBy(UUID clientId) {
        return (root, query, cb) -> cb.equal(root.get("client").get("id"), clientId);
    }

    public static Specification<Request> hasStatus(Request.RequestStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Request> hasApplianceType(Long applianceTypeId) {
        return (root, query, cb) -> applianceTypeId == null ? null
                : cb.equal(root.get("applianceType").get("id"), applianceTypeId);
    }

    public static Specification<Request> createdAfter(LocalDateTime instant) {
        return (root, query, cb) -> instant == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), instant);
    }

    /** Case- and accent-insensitive match on description, brand, model or appliance type name. */
    public static Specification<Request> matches(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) return null;
            String pattern = "%" + escape(fold(search.strip())) + "%";
            var type = root.join("applianceType", JoinType.INNER);
            return cb.or(
                    cb.like(folded(cb, root.get("originalDescription")), pattern, '\\'),
                    cb.like(folded(cb, root.get("brand")), pattern, '\\'),
                    cb.like(folded(cb, root.get("model")), pattern, '\\'),
                    cb.like(folded(cb, type.get("name")), pattern, '\\'));
        };
    }

    /** The description contains at least one of the terms (accent-insensitive). No terms: no filter. */
    public static Specification<Request> descriptionContainsAny(Collection<String> terms) {
        return (root, query, cb) -> {
            if (terms == null || terms.isEmpty()) return null;
            Expression<String> description = folded(cb, root.get("originalDescription"));
            return cb.or(terms.stream()
                    .map(term -> cb.like(description, "%" + escape(fold(term)) + "%", '\\'))
                    .toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    /** lower() plus translate() of accented vowels, available on PostgreSQL and H2 alike. */
    private static Expression<String> folded(CriteriaBuilder cb, Expression<String> value) {
        return cb.function("translate", String.class, cb.lower(value), cb.literal(ACCENTED), cb.literal(PLAIN));
    }

    private static String fold(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
