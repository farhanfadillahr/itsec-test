package com.itsectest.article.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.itsectest.article.domain.Article;
import com.itsectest.article.domain.ArticleRepository;
import com.itsectest.article.domain.ArticleSearchCriteria;
import com.itsectest.article.domain.ArticleVisibility;
import com.itsectest.shared.config.CacheProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Primary
@Repository
public class CachedArticleRepository implements ArticleRepository {

    private static final String KEY_PREFIX = "cache:article:";

    private final ArticleRepository delegate;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CacheProperties properties;

    public CachedArticleRepository(
            @Qualifier("articleJpaAdapter") ArticleRepository delegate,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            CacheProperties properties) {
        this.delegate = delegate;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<Article> findById(UUID id) {
        Optional<Article> cached = readCache(id);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<Article> found = delegate.findById(id);
        found.ifPresent(this::writeCache);
        return found;
    }

    @Override
    public Article save(Article article) {
        Article saved = delegate.save(article);
        evict(saved.getId());
        return saved;
    }

    @Override
    public Page<Article> search(ArticleSearchCriteria criteria, ArticleVisibility visibility, Pageable pageable) {
        return delegate.search(criteria, visibility, pageable);
    }

    @Override
    public long countByAuthor(UUID authorId) {
        return delegate.countByAuthor(authorId);
    }

    private Optional<Article> readCache(UUID id) {
        try {
            String json = redis.opsForValue().get(KEY_PREFIX + id);
            return json == null
                    ? Optional.empty()
                    : Optional.of(objectMapper.readValue(json, CachedArticle.class).toArticle());
        } catch (RuntimeException ex) {
            log.warn("Article cache read failed for {}", id, ex);
            return Optional.empty();
        }
    }

    private void writeCache(Article article) {
        try {
            redis.opsForValue().set(KEY_PREFIX + article.getId(),
                    objectMapper.writeValueAsString(CachedArticle.from(article)),
                    properties.articleTtl());
        } catch (RuntimeException ex) {
            log.warn("Article cache write failed for {}", article.getId(), ex);
        }
    }

    private void evict(UUID id) {
        try {
            redis.delete(KEY_PREFIX + id);
        } catch (RuntimeException ex) {
            log.warn("Article cache eviction failed for {}", id, ex);
        }
    }
}
