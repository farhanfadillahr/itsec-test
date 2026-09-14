package com.itsectest.user.internal;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.itsectest.user.domain.User;
import com.itsectest.user.domain.UserSearchCriteria;

import jakarta.persistence.criteria.Predicate;

final class UserSpecifications {

    private UserSpecifications() {
    }

    static Specification<User> matching(UserSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(root.get("deletedAt")));

            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String pattern = "%" + criteria.keyword().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("fullname")), pattern),
                        builder.like(builder.lower(root.get("username")), pattern),
                        builder.like(builder.lower(root.get("email")), pattern)));
            }
            if (criteria.role() != null) {
                predicates.add(builder.equal(root.get("role"), criteria.role()));
            }
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
