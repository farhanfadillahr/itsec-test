package com.itsectest.audit.internal;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.itsectest.audit.domain.AuditLog;
import com.itsectest.audit.domain.AuditLogSearchCriteria;

import jakarta.persistence.criteria.Predicate;

final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    static Specification<AuditLog> matching(AuditLogSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.actorId() != null) {
                predicates.add(builder.equal(root.get("actorId"), criteria.actorId()));
            }
            if (criteria.actorUsername() != null && !criteria.actorUsername().isBlank()) {
                predicates.add(builder.like(builder.lower(root.get("actorUsername")),
                        "%" + criteria.actorUsername().toLowerCase() + "%"));
            }
            if (criteria.action() != null) {
                predicates.add(builder.equal(root.get("action"), criteria.action()));
            }
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status()));
            }
            if (criteria.resourceType() != null && !criteria.resourceType().isBlank()) {
                predicates.add(builder.equal(root.get("resourceType"), criteria.resourceType()));
            }
            if (criteria.resourceId() != null && !criteria.resourceId().isBlank()) {
                predicates.add(builder.equal(root.get("resourceId"), criteria.resourceId()));
            }
            if (criteria.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), criteria.from()));
            }
            if (criteria.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), criteria.to()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
