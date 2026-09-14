package com.itsectest.article.domain;

import java.util.UUID;

public record ArticleSearchCriteria(String keyword, ArticleStatus status, UUID authorId) {

    public static ArticleSearchCriteria none() {
        return new ArticleSearchCriteria(null, null, null);
    }
}
