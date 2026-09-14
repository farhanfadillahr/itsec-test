package com.itsectest.article.web;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itsectest.article.domain.Article;
import com.itsectest.article.domain.ArticleSearchCriteria;
import com.itsectest.article.domain.ArticleStatus;
import com.itsectest.article.internal.ArticleService;
import com.itsectest.article.internal.CreateArticleCommand;
import com.itsectest.article.internal.UpdateArticleCommand;
import com.itsectest.article.web.dto.ArticleResponse;
import com.itsectest.article.web.dto.ArticleSummaryResponse;
import com.itsectest.article.web.dto.CreateArticleRequest;
import com.itsectest.article.web.dto.UpdateArticleRequest;
import com.itsectest.shared.web.ApiResponse;
import com.itsectest.shared.web.PageResponse;
import com.itsectest.shared.web.PageableFactory;
import com.itsectest.user.api.UserFacade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/articles")
@Validated
@RequiredArgsConstructor
@Tag(name = "Articles", description = "Article CRUD")
public class ArticleController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "title", "status");

    private final ArticleService articles;
    private final UserFacade users;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EDITOR', 'CONTRIBUTOR')")
    @Operation(summary = "Create an article",
            description = "The logged in user becomes the author. Status defaults to DRAFT.")
    public ResponseEntity<ApiResponse<ArticleResponse>> create(
            @Valid @RequestBody CreateArticleRequest request) {

        Article created = articles.create(
                new CreateArticleCommand(request.title(), request.content(), request.status()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Article created", withAuthor(created)));
    }

    @GetMapping
    @Operation(summary = "List articles",
            description = "Returns only the articles the caller is allowed to see.")
    public ApiResponse<PageResponse<ArticleSummaryResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ArticleStatus status,
            @RequestParam(required = false) UUID authorId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Pageable pageable = PageableFactory.of(page, size, sortBy, direction, SORTABLE);
        Page<Article> found = articles.search(new ArticleSearchCriteria(keyword, status, authorId), pageable);

        var authors = users.usernamesOf(found.getContent().stream().map(Article::getAuthorId).toList());
        return ApiResponse.of("Articles retrieved", PageResponse.from(found,
                article -> ArticleSummaryResponse.from(article, authors.get(article.getAuthorId()))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read an article",
            description = "Returns 404 instead of 403 when the caller is not allowed to see the article.")
    public ApiResponse<ArticleResponse> get(@PathVariable UUID id) {
        return ApiResponse.of("Article retrieved", withAuthor(articles.get(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EDITOR', 'CONTRIBUTOR')")
    @Operation(summary = "Update an article",
            description = "Super admin can update any article, other roles only their own.")
    public ApiResponse<ArticleResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateArticleRequest request) {

        Article updated = articles.update(id,
                new UpdateArticleCommand(request.title(), request.content(), request.status()));
        return ApiResponse.of("Article updated", withAuthor(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EDITOR')")
    @Operation(summary = "Delete an article",
            description = "Soft delete. Super admin can delete any article, editors only their own "
                    + "and contributors cannot delete.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        articles.delete(id);
        return ApiResponse.message("Article deleted");
    }

    private ArticleResponse withAuthor(Article article) {
        return ArticleResponse.from(article,
                users.usernamesOf(Set.of(article.getAuthorId())).get(article.getAuthorId()));
    }
}
