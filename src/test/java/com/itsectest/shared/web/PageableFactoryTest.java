package com.itsectest.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.itsectest.shared.error.ApiException;
import com.itsectest.shared.error.ErrorCode;

class PageableFactoryTest {

    private static final Set<String> SORTABLE = Set.of("createdAt", "title");

    @Test
    void buildsTheRequestedPage() {
        Pageable pageable = PageableFactory.of(2, 15, "title", "asc", SORTABLE);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(15);
        assertThat(pageable.getSort().getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void defaultsToNewestFirst() {
        Pageable pageable = PageableFactory.of(0, 20, null, null, SORTABLE);

        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void rejectsAnUnknownSortFieldInsteadOfPassingItToJpa() {
        assertThatThrownBy(() -> PageableFactory.of(0, 20, "password", "asc", SORTABLE))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Cannot sort by 'password'")
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void clampsAbsurdPageSizes() {
        assertThat(PageableFactory.of(0, 5000, null, null, SORTABLE).getPageSize()).isEqualTo(100);
        assertThat(PageableFactory.of(0, 0, null, null, SORTABLE).getPageSize()).isEqualTo(1);
    }

    @Test
    void treatsANegativePageAsTheFirstOne() {
        assertThat(PageableFactory.of(-3, 20, null, null, SORTABLE).getPageNumber()).isZero();
    }
}
