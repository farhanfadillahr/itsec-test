package com.itsectest.article.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.itsectest.article.domain.Article;
import com.itsectest.article.domain.ArticleStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ArticleResponse")
public record ArticleResponse(
        UUID id,
        String title,
        String content,
        UUID authorId,
        @Schema(description = "Null if the author account has since been removed") String authorUsername,
        ArticleStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static ArticleResponse from(Article article, String authorUsername) {
        return new ArticleResponse(article.getId(), article.getTitle(), article.getContent(),
                article.getAuthorId(), authorUsername, article.getStatus(),
                article.getCreatedAt(), article.getUpdatedAt());
    }
}
