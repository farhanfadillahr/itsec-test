package com.itsectest.article.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.itsectest.shared.security.AuthPrincipal;
import com.itsectest.shared.security.Role;

class ArticleAccessPolicyTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID SOMEONE_ELSE = UUID.randomUUID();

    private final ArticleAccessPolicy policy = new ArticleAccessPolicy();

    private static AuthPrincipal principal(Role role, UUID id) {
        return new AuthPrincipal(id, "tester", role);
    }

    private static Article article(UUID authorId, ArticleStatus status) {
        return Article.builder().id(UUID.randomUUID()).title("t").content("c")
                .authorId(authorId).status(status).build();
    }

    @Nested
    @DisplayName("creating")
    class Creating {

        @Test
        void everyRoleButViewerMayCreate() {
            assertThat(policy.canCreate(principal(Role.SUPER_ADMIN, OWNER))).isTrue();
            assertThat(policy.canCreate(principal(Role.EDITOR, OWNER))).isTrue();
            assertThat(policy.canCreate(principal(Role.CONTRIBUTOR, OWNER))).isTrue();
        }

        @Test
        void viewerMayNotCreate() {
            assertThat(policy.canCreate(principal(Role.VIEWER, OWNER))).isFalse();
        }

        @Test
        void anonymousMayNotCreate() {
            assertThat(policy.canCreate(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        void superAdminSeesDraftsTheyDoNotOwn() {
            assertThat(policy.canView(article(SOMEONE_ELSE, ArticleStatus.DRAFT),
                    principal(Role.SUPER_ADMIN, OWNER))).isTrue();
        }

        @Test
        void editorSeesOwnWorkAndPublishedWorkOfOthers() {
            AuthPrincipal editor = principal(Role.EDITOR, OWNER);
            assertThat(policy.canView(article(OWNER, ArticleStatus.DRAFT), editor)).isTrue();
            assertThat(policy.canView(article(SOMEONE_ELSE, ArticleStatus.PUBLISHED), editor)).isTrue();
        }

        @Test
        void editorDoesNotSeeUnpublishedWorkOfOthers() {
            AuthPrincipal editor = principal(Role.EDITOR, OWNER);
            assertThat(policy.canView(article(SOMEONE_ELSE, ArticleStatus.DRAFT), editor)).isFalse();
            assertThat(policy.canView(article(SOMEONE_ELSE, ArticleStatus.ARCHIVED), editor)).isFalse();
        }

        @Test
        void contributorSeesPublishedWorkOfOthers() {
            assertThat(policy.canView(article(SOMEONE_ELSE, ArticleStatus.PUBLISHED),
                    principal(Role.CONTRIBUTOR, OWNER))).isTrue();
        }

        @Test
        void contributorSeesOwnDraft() {
            assertThat(policy.canView(article(OWNER, ArticleStatus.DRAFT),
                    principal(Role.CONTRIBUTOR, OWNER))).isTrue();
        }

        @Test
        void contributorDoesNotSeeSomeoneElsesDraft() {
            assertThat(policy.canView(article(SOMEONE_ELSE, ArticleStatus.DRAFT),
                    principal(Role.CONTRIBUTOR, OWNER))).isFalse();
        }

        @Test
        void viewerSeesOnlyPublished() {
            AuthPrincipal viewer = principal(Role.VIEWER, OWNER);
            assertThat(policy.canView(article(OWNER, ArticleStatus.PUBLISHED), viewer)).isTrue();
            assertThat(policy.canView(article(OWNER, ArticleStatus.DRAFT), viewer)).isFalse();
            assertThat(policy.canView(article(OWNER, ArticleStatus.ARCHIVED), viewer)).isFalse();
        }

        @Test
        void anonymousSeesOnlyPublished() {
            assertThat(policy.canView(article(OWNER, ArticleStatus.PUBLISHED), null)).isTrue();
            assertThat(policy.canView(article(OWNER, ArticleStatus.DRAFT), null)).isFalse();
        }

        @Test
        void aDeletedArticleIsInvisibleEvenToASuperAdmin() {
            Article deleted = article(OWNER, ArticleStatus.PUBLISHED);
            deleted.setDeletedAt(java.time.Instant.now());
            assertThat(policy.canView(deleted, principal(Role.SUPER_ADMIN, OWNER))).isFalse();
        }

        @Test
        void aMissingArticleIsNotViewable() {
            assertThat(policy.canView(null, principal(Role.SUPER_ADMIN, OWNER))).isFalse();
        }
    }

    @Nested
    @DisplayName("updating")
    class Updating {

        @Test
        void superAdminUpdatesAnyArticle() {
            assertThat(policy.canUpdate(article(SOMEONE_ELSE, ArticleStatus.PUBLISHED),
                    principal(Role.SUPER_ADMIN, OWNER))).isTrue();
        }

        @Test
        void editorUpdatesOnlyOwnArticles() {
            assertThat(policy.canUpdate(article(OWNER, ArticleStatus.PUBLISHED),
                    principal(Role.EDITOR, OWNER))).isTrue();
            assertThat(policy.canUpdate(article(SOMEONE_ELSE, ArticleStatus.PUBLISHED),
                    principal(Role.EDITOR, OWNER))).isFalse();
        }

        @Test
        void contributorUpdatesOnlyOwnArticles() {
            assertThat(policy.canUpdate(article(OWNER, ArticleStatus.DRAFT),
                    principal(Role.CONTRIBUTOR, OWNER))).isTrue();
            assertThat(policy.canUpdate(article(SOMEONE_ELSE, ArticleStatus.DRAFT),
                    principal(Role.CONTRIBUTOR, OWNER))).isFalse();
        }

        @Test
        void viewerUpdatesNothing() {
            assertThat(policy.canUpdate(article(OWNER, ArticleStatus.PUBLISHED),
                    principal(Role.VIEWER, OWNER))).isFalse();
        }

        @Test
        void anonymousUpdatesNothing() {
            assertThat(policy.canUpdate(article(OWNER, ArticleStatus.PUBLISHED), null)).isFalse();
        }
    }

    @Nested
    @DisplayName("deleting")
    class Deleting {

        @Test
        void superAdminDeletesAnyArticle() {
            assertThat(policy.canDelete(article(SOMEONE_ELSE, ArticleStatus.PUBLISHED),
                    principal(Role.SUPER_ADMIN, OWNER))).isTrue();
        }

        @Test
        void editorDeletesOnlyOwnArticles() {
            assertThat(policy.canDelete(article(OWNER, ArticleStatus.PUBLISHED),
                    principal(Role.EDITOR, OWNER))).isTrue();
            assertThat(policy.canDelete(article(SOMEONE_ELSE, ArticleStatus.PUBLISHED),
                    principal(Role.EDITOR, OWNER))).isFalse();
        }

        @Test
        void contributorNeverDeletes() {
            assertThat(policy.canDelete(article(OWNER, ArticleStatus.DRAFT),
                    principal(Role.CONTRIBUTOR, OWNER))).isFalse();
        }

        @Test
        void viewerNeverDeletes() {
            assertThat(policy.canDelete(article(OWNER, ArticleStatus.PUBLISHED),
                    principal(Role.VIEWER, OWNER))).isFalse();
        }

        @Test
        void anonymousNeverDeletes() {
            assertThat(policy.canDelete(article(OWNER, ArticleStatus.PUBLISHED), null)).isFalse();
        }
    }

    @Nested
    @DisplayName("listing visibility")
    class Visibility {

        @Test
        void superAdminSeesEveryStatus() {
            assertThat(ArticleVisibility.forCaller(principal(Role.SUPER_ADMIN, OWNER)).allStatuses()).isTrue();
        }

        @Test
        void editorGetsPublishedPlusOwn() {
            ArticleVisibility visibility = ArticleVisibility.forCaller(principal(Role.EDITOR, OWNER));
            assertThat(visibility.allStatuses()).isFalse();
            assertThat(visibility.ownerId()).isEqualTo(OWNER);
        }

        @Test
        void contributorGetsPublishedPlusOwn() {
            ArticleVisibility visibility = ArticleVisibility.forCaller(principal(Role.CONTRIBUTOR, OWNER));
            assertThat(visibility.allStatuses()).isFalse();
            assertThat(visibility.ownerId()).isEqualTo(OWNER);
        }

        @Test
        void viewerGetsPublishedOnly() {
            ArticleVisibility visibility = ArticleVisibility.forCaller(principal(Role.VIEWER, OWNER));
            assertThat(visibility.allStatuses()).isFalse();
            assertThat(visibility.ownerId()).isNull();
        }

        @Test
        void anonymousGetsPublishedOnly() {
            assertThat(ArticleVisibility.forCaller(null).allStatuses()).isFalse();
            assertThat(ArticleVisibility.all().allStatuses()).isTrue();
        }
    }
}
