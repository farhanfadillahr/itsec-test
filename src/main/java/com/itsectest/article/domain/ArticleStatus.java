package com.itsectest.article.domain;

public enum ArticleStatus {

    DRAFT,

    PUBLISHED,

    ARCHIVED;

    public boolean isPublic() {
        return this == PUBLISHED;
    }
}
