package com.itsectest.article.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;

import com.itsectest.article.domain.Article;
import com.itsectest.article.domain.ArticleStatus;
import com.itsectest.article.internal.ArticleService;
import com.itsectest.article.web.dto.ArticleSummaryResponse;
import com.itsectest.article.web.dto.CreateArticleRequest;
import com.itsectest.article.web.dto.UpdateArticleRequest;
import com.itsectest.user.api.UserFacade;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArticleControllerTest {

    private static final UUID ARTICLE_ID = UUID.randomUUID();
    private static final UUID AUTHOR_ID = UUID.randomUUID();

    @Mock private ArticleService articles;
    @Mock private UserFacade users;

    @InjectMocks private ArticleController controller;

    private static Article article(String content) {
        return Article.builder().id(ARTICLE_ID).title("Title").content(content)
                .authorId(AUTHOR_ID).status(ArticleStatus.PUBLISHED)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void creatingAnArticleAnswersWithCreatedAndTheAuthorName() {
        when(articles.create(any())).thenReturn(article("Body"));
        when(users.usernamesOf(any())).thenReturn(Map.of(AUTHOR_ID, "farhan"));

        var response = controller.create(new CreateArticleRequest("Title", "Body", ArticleStatus.PUBLISHED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().authorUsername()).isEqualTo("farhan");
    }

    @Test
    void theListingResolvesEveryAuthorInASingleLookup() {
        when(articles.search(any(), any())).thenReturn(new PageImpl<>(List.of(article("Body"), article("Body"))));
        when(users.usernamesOf(any())).thenReturn(Map.of(AUTHOR_ID, "farhan"));

        var page = controller.list(null, null, null, 0, 20, "createdAt", "desc").data();

        assertThat(page.items()).hasSize(2);
        verify(users).usernamesOf(any());
    }

    @Test
    void theListingCarriesAnExcerptRatherThanTheWholeBody() {
        String body = "x".repeat(500);
        when(articles.search(any(), any())).thenReturn(new PageImpl<>(List.of(article(body))));
        when(users.usernamesOf(any())).thenReturn(Map.of(AUTHOR_ID, "farhan"));

        ArticleSummaryResponse summary = controller.list(null, null, null, 0, 20, "createdAt", "desc")
                .data().items().getFirst();

        assertThat(summary.excerpt()).hasSize(201).endsWith("…");
    }

    @Test
    void aShortArticleIsNotTruncated() {
        when(articles.search(any(), any())).thenReturn(new PageImpl<>(List.of(article("Short body"))));
        when(users.usernamesOf(any())).thenReturn(Map.of());

        assertThat(controller.list(null, null, null, 0, 20, "createdAt", "desc")
                .data().items().getFirst().excerpt()).isEqualTo("Short body");
    }

    @Test
    void readingAnArticleReturnsTheFullBody() {
        when(articles.get(ARTICLE_ID)).thenReturn(article("Full body"));
        when(users.usernamesOf(any())).thenReturn(Map.of(AUTHOR_ID, "farhan"));

        assertThat(controller.get(ARTICLE_ID).data().content()).isEqualTo("Full body");
    }

    @Test
    void anArticleWhoseAuthorIsGoneStillRenders() {
        when(articles.get(ARTICLE_ID)).thenReturn(article("Body"));
        when(users.usernamesOf(any())).thenReturn(Map.of());

        assertThat(controller.get(ARTICLE_ID).data().authorUsername()).isNull();
    }

    @Test
    void updatingReturnsTheUpdatedArticle() {
        when(articles.update(any(), any())).thenReturn(article("New body"));
        when(users.usernamesOf(any())).thenReturn(Map.of(AUTHOR_ID, "farhan"));

        assertThat(controller.update(ARTICLE_ID,
                new UpdateArticleRequest("Title", "New body", null)).data().content())
                .isEqualTo("New body");
    }

    @Test
    void deletingConfirmsWithoutABody() {
        var response = controller.delete(ARTICLE_ID);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNull();
        verify(articles).delete(ARTICLE_ID);
    }
}
