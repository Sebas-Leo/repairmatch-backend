package com.repairmatch.repairmatch_backend.repository;

import com.repairmatch.repairmatch_backend.model.Request;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.UUID;

/** Filters for the client's own request list. Each one is optional and they combine with AND. */
public final class RequestSpecifications {
    private RequestSpecifications() {
    }

    public static Specification<Request> ownedBy(UUID clientId) {
        return (root, query, cb) -> cb.equal(root.get("client").get("id"), clientId);
    }

    public static Specification<Request> hasStatus(Request.RequestStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    /** Case-insensitive match on description, brand, model or appliance type name. */
    public static Specification<Request> matches(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) return null;
            String pattern = "%" + escape(search.strip().toLowerCase(Locale.ROOT)) + "%";
            var type = root.join("applianceType", JoinType.INNER);
            return cb.or(
                    cb.like(cb.lower(root.get("originalDescription")), pattern, '\\'),
                    cb.like(cb.lower(root.get("brand")), pattern, '\\'),
                    cb.like(cb.lower(root.get("model")), pattern, '\\'),
                    cb.like(cb.lower(type.get("name")), pattern, '\\'));
        };
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
