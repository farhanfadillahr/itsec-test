package com.itsectest.article.domain;

import org.springframework.stereotype.Component;

import com.itsectest.shared.security.AuthPrincipal;

@Component
public class ArticleAccessPolicy {

    public boolean canCreate(AuthPrincipal principal) {
        return principal != null && switch (principal.role()) {
            case SUPER_ADMIN, EDITOR, CONTRIBUTOR -> true;
            case VIEWER -> false;
        };
    }

    public boolean canView(Article article, AuthPrincipal principal) {
        if (article == null || article.isDeleted()) {
            return false;
        }
        if (principal == null) {
            return article.isPublic();
        }
        return switch (principal.role()) {
            case SUPER_ADMIN -> true;
            case EDITOR, CONTRIBUTOR -> article.isPublic() || article.isOwnedBy(principal.userId());
            case VIEWER -> article.isPublic();
        };
    }

    public boolean canUpdate(Article article, AuthPrincipal principal) {
        if (article == null || article.isDeleted() || principal == null) {
            return false;
        }
        return switch (principal.role()) {
            case SUPER_ADMIN -> true;
            case EDITOR, CONTRIBUTOR -> article.isOwnedBy(principal.userId());
            case VIEWER -> false;
        };
    }

    public boolean canDelete(Article article, AuthPrincipal principal) {
        if (article == null || article.isDeleted() || principal == null) {
            return false;
        }
        return switch (principal.role()) {
            case SUPER_ADMIN -> true;
            case EDITOR -> article.isOwnedBy(principal.userId());
            case CONTRIBUTOR, VIEWER -> false;
        };
    }
}
