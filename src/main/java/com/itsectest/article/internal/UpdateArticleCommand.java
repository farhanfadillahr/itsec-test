package com.itsectest.article.internal;

import com.itsectest.article.domain.ArticleStatus;

public record UpdateArticleCommand(String title, String content, ArticleStatus status) {
}
