package com.itsectest.article.domain;

import java.util.UUID;

import com.itsectest.shared.security.AuthPrincipal;
import com.itsectest.shared.security.Role;

public record ArticleVisibility(boolean allStatuses, UUID ownerId) {

    public static ArticleVisibility forCaller(AuthPrincipal principal) {
        if (principal == null) {
            return new ArticleVisibility(false, null);
        }
        return switch (principal.role()) {
            case SUPER_ADMIN, EDITOR -> new ArticleVisibility(true, null);
            case CONTRIBUTOR -> new ArticleVisibility(false, principal.userId());
            case VIEWER -> new ArticleVisibility(false, null);
        };
    }

    public static ArticleVisibility all() {
        return new ArticleVisibility(true, null);
    }

    static boolean readsEverything(Role role) {
        return role == Role.SUPER_ADMIN || role == Role.EDITOR;
    }
}
