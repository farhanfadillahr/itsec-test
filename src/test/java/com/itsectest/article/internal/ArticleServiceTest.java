package com.itsectest.article.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.itsectest.article.domain.Article;
import com.itsectest.article.domain.ArticleAccessPolicy;
import com.itsectest.article.domain.ArticleRepository;
import com.itsectest.article.domain.ArticleSearchCriteria;
import com.itsectest.article.domain.ArticleStatus;
import com.itsectest.article.domain.ArticleVisibility;
import com.itsectest.shared.error.ForbiddenException;
import com.itsectest.shared.error.NotFoundException;
import com.itsectest.shared.security.AuthPrincipal;
import com.itsectest.shared.security.CurrentUserProvider;
import com.itsectest.shared.security.Role;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArticleServiceTest {

    private static final UUID ARTICLE_ID = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID SOMEONE_ELSE = UUID.randomUUID();

    @Mock private ArticleRepository articles;
    @Mock private CurrentUserProvider currentUser;

    private ArticleService service;

    private static Article article(UUID authorId, ArticleStatus status) {
        return Article.builder().id(ARTICLE_ID).title("Title").content("Body")
                .authorId(authorId).status(status).build();
    }

    private void callerIs(Role role, UUID id) {
        when(currentUser.require()).thenReturn(new AuthPrincipal(id, "tester", role));
    }

    @BeforeEach
    void setUp() {
        service = new ArticleService(articles, new ArticleAccessPolicy(), currentUser);
        when(articles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsAnArticleOwnedByTheCaller() {
        callerIs(Role.CONTRIBUTOR, OWNER);

        Article created = service.create(new CreateArticleCommand("  Title  ", "Body", ArticleStatus.PUBLISHED));

        assertThat(created.getAuthorId()).isEqualTo(OWNER);
        assertThat(created.getTitle()).isEqualTo("Title");
        assertThat(created.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
    }

    @Test
    void defaultsANewArticleToDraft() {
        callerIs(Role.EDITOR, OWNER);

        assertThat(service.create(new CreateArticleCommand("Title", "Body", null)).getStatus())
                .isEqualTo(ArticleStatus.DRAFT);
    }

    @Test
    void refusesCreationByAViewer() {
        callerIs(Role.VIEWER, OWNER);

        assertThatThrownBy(() -> service.create(new CreateArticleCommand("Title", "Body", null)))
                .isInstanceOf(ForbiddenException.class);
        verify(articles, never()).save(any());
    }

    @Test
    void returnsAnArticleTheCallerMaySee() {
        callerIs(Role.VIEWER, OWNER);
        when(articles.findById(ARTICLE_ID)).thenReturn(Optional.of(article(SOMEONE_ELSE, ArticleStatus.PUBLISHED)));

        assertThat(service.get(ARTICLE_ID).getTitle()).isEqualTo("Title");
    }

    @Test
    void hidesADraftFromAViewerAsNotFoundRatherThanForbidden() {
        callerIs(Role.VIEWER, OWNER);
        when(articles.findById(ARTICLE_ID)).thenReturn(Optional.of(article(SOMEONE_ELSE, ArticleStatus.DRAFT)));

        assertThatThrownBy(() -> service.get(ARTICLE_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Article not found");
    }

    @Test
    void reportsAGenuinelyMissingArticle() {
        callerIs(Role.SUPER_ADMIN, OWNER);
        when(articles.findById(ARTICLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ARTICLE_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void scopesTheListingToWhatTheCallerMaySee() {
        callerIs(Role.CONTRIBUTOR, OWNER);
        when(articles.search(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(article(OWNER, ArticleStatus.DRAFT))));

        service.search(ArticleSearchCriteria.none(), PageRequest.of(0, 20));

        ArgumentCaptor<ArticleVisibility> visibility = ArgumentCaptor.captor();
        verify(articles).search(any(), visibility.capture(), any());
        assertThat(visibility.getValue().allStatuses()).isFalse();
        assertThat(visibility.getValue().ownerId()).isEqualTo(OWNER);
    }

    @Test
    void letsAnEditorSeeEverythingInTheListing() {
        callerIs(Role.EDITOR, OWNER);
        when(articles.search(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.search(ArticleSearchCriteria.none(), PageRequest.of(0, 20));

        ArgumentCaptor<ArticleVisibility> visibility = ArgumentCaptor.captor();
        verify(articles).search(any(), visibility.capture(), any());
        assertThat(visibility.getValue().allStatuses()).isTrue();
    }

    @Test
    void letsAnEditorReadButNotChangeSomeoneElsesDraft() {
        callerIs(Role.EDITOR, OWNER);
        when(articles.findById(ARTICLE_ID)).thenReturn(Optional.of(article(SOMEONE_ELSE, ArticleStatus.DRAFT)));

        assertThat(service.get(ARTICLE_ID).getAuthorId()).isEqualTo(SOMEONE_ELSE);
        assertThatThrownBy(() -> service.update(ARTICLE_ID, new UpdateArticleCommand("T", "B", null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updatesAnArticleTheCallerOwns() {
        callerIs(Role.CONTRIBUTOR, OWNER);
        when(articles.findById(ARTICLE_ID)).thenReturn(Optional.of(article(OWNER, ArticleStatus.DRAFT)));

        Article updated = service.update(ARTICLE_ID,
                new UpdateArticleCommand("  New title ", "New body", ArticleStatus.PUBLISHED));

        assertThat(updated.getTitle()).isEqualTo("New title");
        assertThat(updated.getContent()).isEqualTo("New body");
        assertThat(updated.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
    }

    @Test
    void leavesTheStatusAloneWhenTheUpdateOmitsIt() {
        callerIs(Role.SUPER_ADMIN, OWNER);
        when(articles.findById(ARTICLE_ID)).thenReturn(Optional.of(article(SOMEONE_ELSE, ArticleStatus.ARCHIVED)));

        assertThat(service.update(ARTICLE_ID, new UpdateArticleCommand("T", "B", null)).getStatus())
                .isEqualTo(ArticleStatus.ARCHIVED);
    }

    @Test
    void refusesToLetAnEditorChangeSomeoneElsesArticle() {
        callerIs(Role.EDITOR, OWNER);
        when(articles.findById(ARTICLE_ID)).thenReturn(Optional.of(article(SOMEONE_ELSE, ArticleStatus.PUBLISHED)));

        assertThatThrownBy(() -> service.update(ARTICLE_ID, new UpdateArticleCommand("T", "B", null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("only change articles you own");
    }

    @Test
    void hidesAnInvisibleArticleOnUpdateToo() {
        callerIs(Role.CONTRIBUTOR, OWNER);
        when(articles.findById(ARTICLE_ID)).thenReturn(Optional.of(article(SOMEONE_ELSE, ArticleStatus.DRAFT)));

        assertThatThrownBy(() -> service.update(ARTICLE_ID, new UpdateArticleCommand("T", "B", null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void softDeletesRatherThanRemovingTheRow() {
        callerIs(Role.SUPER_ADMIN, OWNER);
        Article target = article(SOMEONE_ELSE, ArticleStatus.PUBLISHED);
        when(articles.findById(ARTICLE_ID)).thenReturn(Optional.of(target));

        service.delete(ARTICLE_ID);

        assertThat(target.getDeletedAt()).isNotNull();
        assertThat(target.isDeleted()).isTrue();
        verify(articles).save(target);
    }

    @Test
    void refusesDeletionByAContributor() {
        callerIs(Role.CONTRIBUTOR, OWNER);
        when(articles.findById(ARTICLE_ID)).thenReturn(Optional.of(article(OWNER, ArticleStatus.DRAFT)));

        assertThatThrownBy(() -> service.delete(ARTICLE_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("cannot delete");
    }

    @Test
    void refusesToLetAnEditorDeleteSomeoneElsesArticle() {
        callerIs(Role.EDITOR, OWNER);
        when(articles.findById(ARTICLE_ID)).thenReturn(Optional.of(article(SOMEONE_ELSE, ArticleStatus.PUBLISHED)));

        assertThatThrownBy(() -> service.delete(ARTICLE_ID)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void reportsAMissingArticleOnDelete() {
        callerIs(Role.SUPER_ADMIN, OWNER);
        when(articles.findById(ARTICLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(ARTICLE_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void entityHelpersBehave() {
        Article target = article(OWNER, ArticleStatus.PUBLISHED);

        assertThat(target.isPublic()).isTrue();
        assertThat(target.isOwnedBy(OWNER)).isTrue();
        assertThat(target.isOwnedBy(SOMEONE_ELSE)).isFalse();
        assertThat(target.isDeleted()).isFalse();
        assertThat(target).isEqualTo(article(SOMEONE_ELSE, ArticleStatus.DRAFT));
        assertThat(target).isNotEqualTo("not an article");
        assertThat(target.hashCode()).isEqualTo(ARTICLE_ID.hashCode());
        assertThat(ArticleStatus.DRAFT.isPublic()).isFalse();

        target.setDeletedAt(Instant.now());
        assertThat(target.isDeleted()).isTrue();
    }
}
