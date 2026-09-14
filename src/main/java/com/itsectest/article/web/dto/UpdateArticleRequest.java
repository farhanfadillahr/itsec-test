package com.itsectest.article.web.dto;

import com.itsectest.article.domain.ArticleStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateArticleRequest")
public record UpdateArticleRequest(

        @NotBlank @Size(max = 200) String title,

        @NotBlank @Size(max = 100_000) String content,

        @Schema(description = "Omit to leave the current status unchanged", nullable = true)
        ArticleStatus status) {
}
