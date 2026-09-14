package com.itsectest.article.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ArticleRepository {

    Article save(Article article);

    Optional<Article> findById(UUID id);

    Page<Article> search(ArticleSearchCriteria criteria, ArticleVisibility visibility, Pageable pageable);

    long countByAuthor(UUID authorId);
}
