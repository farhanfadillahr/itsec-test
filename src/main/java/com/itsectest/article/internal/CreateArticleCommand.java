package com.itsectest.article.internal;

import com.itsectest.article.domain.ArticleStatus;

public record CreateArticleCommand(String title, String content, ArticleStatus status) {
}
