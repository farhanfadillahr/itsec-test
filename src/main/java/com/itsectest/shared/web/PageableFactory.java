package com.itsectest.shared.web;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.itsectest.shared.error.ApiException;
import com.itsectest.shared.error.ErrorCode;

public final class PageableFactory {

    private static final int MAX_PAGE_SIZE = 100;

    private PageableFactory() {
    }

    public static Pageable of(int page, int size, String sortBy, String direction, Set<String> sortable) {
        if (sortBy != null && !sortBy.isBlank() && !sortable.contains(sortBy)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Cannot sort by '" + sortBy + "'. Allowed: " + String.join(", ", sortable));
        }
        String field = (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy;
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(sortDirection, field));
    }
}
