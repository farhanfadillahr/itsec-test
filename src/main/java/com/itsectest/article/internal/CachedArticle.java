package com.itsectest.article.internal;

import java.time.Instant;
import java.util.UUID;

import com.itsectest.article.domain.Article;
import com.itsectest.article.domain.ArticleStatus;

record CachedArticle(
        UUID id,
        String title,
        String content,
        UUID authorId,
        ArticleStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    static CachedArticle from(Article article) {
        return new CachedArticle(article.getId(), article.getTitle(), article.getContent(),
                article.getAuthorId(), article.getStatus(), article.getCreatedAt(),
                article.getUpdatedAt(), article.getDeletedAt());
    }

    Article toArticle() {
        return Article.builder()
                .id(id)
                .title(title)
                .content(content)
                .authorId(authorId)
                .status(status)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .deletedAt(deletedAt)
                .build();
    }
}
