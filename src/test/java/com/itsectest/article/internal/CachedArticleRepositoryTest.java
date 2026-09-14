package com.itsectest.article.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.itsectest.article.domain.Article;
import com.itsectest.article.domain.ArticleRepository;
import com.itsectest.article.domain.ArticleSearchCriteria;
import com.itsectest.article.domain.ArticleStatus;
import com.itsectest.article.domain.ArticleVisibility;
import com.itsectest.shared.config.CacheProperties;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CachedArticleRepositoryTest {

    private static final UUID ARTICLE_ID = UUID.randomUUID();
    private static final String KEY = "cache:article:" + ARTICLE_ID;

    @Mock private ArticleRepository delegate;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private CachedArticleRepository repository;

    private static Article article() {
        return Article.builder().id(ARTICLE_ID).title("Title").content("Body")
                .authorId(UUID.randomUUID()).status(ArticleStatus.PUBLISHED)
                .createdAt(Instant.parse("2026-09-01T10:15:30Z"))
                .updatedAt(Instant.parse("2026-09-02T10:15:30Z"))
                .build();
    }

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        repository = new CachedArticleRepository(delegate, redis, mapper,
                new CacheProperties(Duration.ofMinutes(10)));
    }

    @Test
    void readsThroughToTheDatabaseOnAMissAndCachesTheResult() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(delegate.findById(ARTICLE_ID)).thenReturn(Optional.of(article()));

        assertThat(repository.findById(ARTICLE_ID)).isPresent();

        verify(delegate).findById(ARTICLE_ID);
        verify(valueOps).set(eq(KEY), anyString(), eq(Duration.ofMinutes(10)));
    }

    @Test
    void servesAHitWithoutTouchingTheDatabase() {
        when(valueOps.get(KEY)).thenReturn(mapper.writeValueAsString(CachedArticle.from(article())));

        Optional<Article> found = repository.findById(ARTICLE_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Title");
        assertThat(found.get().getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(found.get().getCreatedAt()).isEqualTo(Instant.parse("2026-09-01T10:15:30Z"));
        verify(delegate, never()).findById(any());
    }

    @Test
    void doesNotCacheAnArticleThatIsNotThere() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(delegate.findById(ARTICLE_ID)).thenReturn(Optional.empty());

        assertThat(repository.findById(ARTICLE_ID)).isEmpty();
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void evictsOnWriteRatherThanWritingThrough() {
        Article saved = article();
        when(delegate.save(saved)).thenReturn(saved);

        repository.save(saved);

        verify(redis).delete(KEY);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void leavesListingsToTheDatabase() {
        when(delegate.search(any(), any(), any())).thenReturn(new PageImpl<>(List.of(article())));

        assertThat(repository.search(ArticleSearchCriteria.none(), ArticleVisibility.all(),
                PageRequest.of(0, 20))).hasSize(1);
        assertThat(repository.countByAuthor(ARTICLE_ID)).isZero();
        verify(delegate).countByAuthor(ARTICLE_ID);
    }

    @Test
    void fallsBackToTheDatabaseWhenTheCacheReadFails() {
        when(valueOps.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));
        when(delegate.findById(ARTICLE_ID)).thenReturn(Optional.of(article()));

        assertThat(repository.findById(ARTICLE_ID)).isPresent();
    }

    @Test
    void ignoresCorruptCachedContent() {
        when(valueOps.get(KEY)).thenReturn("{not json");
        when(delegate.findById(ARTICLE_ID)).thenReturn(Optional.of(article()));

        assertThat(repository.findById(ARTICLE_ID)).isPresent();
        verify(delegate).findById(ARTICLE_ID);
    }

    @Test
    void aFailedCacheWriteDoesNotFailTheRead() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(delegate.findById(ARTICLE_ID)).thenReturn(Optional.of(article()));
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        assertThat(repository.findById(ARTICLE_ID)).isPresent();
    }

    @Test
    void aFailedEvictionDoesNotFailTheWrite() {
        Article saved = article();
        when(delegate.save(saved)).thenReturn(saved);
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(repository.save(saved)).isSameAs(saved);
    }

    @Test
    void roundTripsEveryFieldThroughTheCacheForm() {
        Article original = article();
        original.setDeletedAt(Instant.parse("2026-09-03T10:15:30Z"));

        Article restored = mapper.readValue(
                mapper.writeValueAsString(CachedArticle.from(original)), CachedArticle.class).toArticle();

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getAuthorId()).isEqualTo(original.getAuthorId());
        assertThat(restored.getContent()).isEqualTo(original.getContent());
        assertThat(restored.getDeletedAt()).isEqualTo(original.getDeletedAt());
    }

    @Test
    void suppliesADefaultTtlWhenNoneIsConfigured() {
        assertThat(new CacheProperties(null).articleTtl()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void cachesUnderAKeyScopedToTheArticle() {
        when(valueOps.get(KEY)).thenReturn(null);
        when(delegate.findById(ARTICLE_ID)).thenReturn(Optional.of(article()));

        repository.findById(ARTICLE_ID);

        ArgumentCaptor<String> key = ArgumentCaptor.captor();
        verify(valueOps).set(key.capture(), anyString(), any(Duration.class));
        assertThat(key.getValue()).isEqualTo("cache:article:" + ARTICLE_ID);
    }
}
