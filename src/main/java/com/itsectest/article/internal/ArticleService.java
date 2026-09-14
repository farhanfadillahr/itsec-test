package com.itsectest.article.internal;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itsectest.article.domain.Article;
import com.itsectest.article.domain.ArticleAccessPolicy;
import com.itsectest.article.domain.ArticleRepository;
import com.itsectest.article.domain.ArticleSearchCriteria;
import com.itsectest.article.domain.ArticleStatus;
import com.itsectest.article.domain.ArticleVisibility;
import com.itsectest.shared.audit.api.AuditAction;
import com.itsectest.shared.audit.api.Auditable;
import com.itsectest.shared.error.ForbiddenException;
import com.itsectest.shared.error.NotFoundException;
import com.itsectest.shared.security.AuthPrincipal;
import com.itsectest.shared.security.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class ArticleService {

    private static final String NOT_FOUND = "Article not found";

    private final ArticleRepository articles;
    private final ArticleAccessPolicy policy;
    private final CurrentUserProvider currentUser;

    @Auditable(action = AuditAction.ARTICLE_CREATED, resourceType = "ARTICLE", resourceId = "#result.id")
    public Article create(CreateArticleCommand command) {
        AuthPrincipal principal = currentUser.require();
        if (!policy.canCreate(principal)) {
            throw new ForbiddenException("Your role cannot create articles");
        }

        return articles.save(Article.builder()
                .title(command.title().trim())
                .content(command.content())
                .authorId(principal.userId())
                .status(command.status() == null ? ArticleStatus.DRAFT : command.status())
                .build());
    }

    @Transactional(readOnly = true)
    @Auditable(action = AuditAction.ARTICLE_VIEWED, resourceType = "ARTICLE", resourceId = "#id")
    public Article get(UUID id) {
        AuthPrincipal principal = currentUser.require();
        Article article = articles.findById(id).orElseThrow(() -> new NotFoundException(NOT_FOUND));

        // 404 instead of 403, so the caller cannot tell that the draft exists
        if (!policy.canView(article, principal)) {
            throw new NotFoundException(NOT_FOUND);
        }
        return article;
    }

    @Transactional(readOnly = true)
    @Auditable(action = AuditAction.ARTICLE_LIST_VIEWED, resourceType = "ARTICLE")
    public Page<Article> search(ArticleSearchCriteria criteria, Pageable pageable) {
        AuthPrincipal principal = currentUser.require();
        return articles.search(criteria, ArticleVisibility.forCaller(principal), pageable);
    }

    @Auditable(action = AuditAction.ARTICLE_UPDATED, resourceType = "ARTICLE", resourceId = "#id")
    public Article update(UUID id, UpdateArticleCommand command) {
        AuthPrincipal principal = currentUser.require();
        Article article = articles.findById(id).orElseThrow(() -> new NotFoundException(NOT_FOUND));

        if (!policy.canView(article, principal)) {
            throw new NotFoundException(NOT_FOUND);
        }
        if (!policy.canUpdate(article, principal)) {
            throw new ForbiddenException("You may only change articles you own");
        }

        article.setTitle(command.title().trim());
        article.setContent(command.content());
        if (command.status() != null) {
            article.setStatus(command.status());
        }
        return articles.save(article);
    }

    @Auditable(action = AuditAction.ARTICLE_DELETED, resourceType = "ARTICLE", resourceId = "#id")
    public void delete(UUID id) {
        AuthPrincipal principal = currentUser.require();
        Article article = articles.findById(id).orElseThrow(() -> new NotFoundException(NOT_FOUND));

        if (!policy.canView(article, principal)) {
            throw new NotFoundException(NOT_FOUND);
        }
        if (!policy.canDelete(article, principal)) {
            throw new ForbiddenException("Your role cannot delete this article");
        }

        article.setDeletedAt(Instant.now());
        articles.save(article);
    }
}
