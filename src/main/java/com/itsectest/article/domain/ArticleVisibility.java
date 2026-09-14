package com.itsectest.article.domain;

import java.util.UUID;

import com.itsectest.shared.security.AuthPrincipal;

public record ArticleVisibility(boolean allStatuses, UUID ownerId) {

    public static ArticleVisibility forCaller(AuthPrincipal principal) {
        if (principal == null) {
            return new ArticleVisibility(false, null);
        }
        return switch (principal.role()) {
            case SUPER_ADMIN -> new ArticleVisibility(true, null);
            case EDITOR, CONTRIBUTOR -> new ArticleVisibility(false, principal.userId());
            case VIEWER -> new ArticleVisibility(false, null);
        };
    }

    public static ArticleVisibility all() {
        return new ArticleVisibility(true, null);
    }
}
