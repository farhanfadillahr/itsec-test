package com.itsectest.article.web.dto;

import com.itsectest.article.domain.ArticleStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateArticleRequest")
public record CreateArticleRequest(

        @NotBlank @Size(max = 200)
        @Schema(example = "Designing a modular monolith")
        String title,

        @NotBlank @Size(max = 100_000)
        @Schema(example = "Package by feature, not by layer...")
        String content,

        @Schema(description = "Defaults to DRAFT when omitted", example = "PUBLISHED", nullable = true)
        ArticleStatus status) {
}
