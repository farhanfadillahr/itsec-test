package com.itsectest.article.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.itsectest.article.domain.Article;
import com.itsectest.article.domain.ArticleRepository;
import com.itsectest.article.domain.ArticleSearchCriteria;
import com.itsectest.article.domain.ArticleVisibility;

import lombok.RequiredArgsConstructor;

@Repository("articleJpaAdapter")
@RequiredArgsConstructor
class ArticleJpaAdapter implements ArticleRepository {

    private final ArticleJpaRepository jpa;

    @Override
    public Article save(Article article) {
        return jpa.save(article);
    }

    @Override
    public Optional<Article> findById(UUID id) {
        return jpa.findById(id).filter(article -> !article.isDeleted());
    }

    @Override
    public Page<Article> search(ArticleSearchCriteria criteria, ArticleVisibility visibility, Pageable pageable) {
        return jpa.findAll(ArticleSpecifications.matching(criteria, visibility), pageable);
    }

    @Override
    public long countByAuthor(UUID authorId) {
        return jpa.countByAuthorIdAndDeletedAtIsNull(authorId);
    }
}
