package com.itsectest.article.internal;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.itsectest.article.domain.Article;
import com.itsectest.article.domain.ArticleSearchCriteria;
import com.itsectest.article.domain.ArticleStatus;
import com.itsectest.article.domain.ArticleVisibility;

import jakarta.persistence.criteria.Predicate;

final class ArticleSpecifications {

    private ArticleSpecifications() {
    }

    static Specification<Article> matching(ArticleSearchCriteria criteria, ArticleVisibility visibility) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(root.get("deletedAt")));

            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String pattern = "%" + criteria.keyword().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("content")), pattern)));
            }
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status()));
            }
            if (criteria.authorId() != null) {
                predicates.add(builder.equal(root.get("authorId"), criteria.authorId()));
            }

            if (!visibility.allStatuses()) {
                Predicate published = builder.equal(root.get("status"), ArticleStatus.PUBLISHED);
                predicates.add(visibility.ownerId() == null
                        ? published
                        : builder.or(published, builder.equal(root.get("authorId"), visibility.ownerId())));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
