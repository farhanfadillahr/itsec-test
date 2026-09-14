package com.itsectest.article.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.itsectest.article.domain.Article;
import com.itsectest.article.domain.ArticleStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ArticleSummaryResponse")
public record ArticleSummaryResponse(
        UUID id,
        String title,
        @Schema(description = "First 200 characters of the body") String excerpt,
        UUID authorId,
        String authorUsername,
        ArticleStatus status,
        Instant createdAt,
        Instant updatedAt) {

    private static final int EXCERPT_LENGTH = 200;

    public static ArticleSummaryResponse from(Article article, String authorUsername) {
        return new ArticleSummaryResponse(article.getId(), article.getTitle(),
                excerpt(article.getContent()), article.getAuthorId(), authorUsername,
                article.getStatus(), article.getCreatedAt(), article.getUpdatedAt());
    }

    private static String excerpt(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= EXCERPT_LENGTH
                ? content
                : content.substring(0, EXCERPT_LENGTH).stripTrailing() + "…";
    }
}
