package com.itsectest.article.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.itsectest.article.domain.Article;

interface ArticleJpaRepository extends JpaRepository<Article, UUID>, JpaSpecificationExecutor<Article> {

    long countByAuthorIdAndDeletedAtIsNull(UUID authorId);
}
