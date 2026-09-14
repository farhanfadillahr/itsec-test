package com.itsectest.user.domain;

import com.itsectest.shared.security.Role;

public record UserSearchCriteria(String keyword, Role role, UserStatus status) {

    public static UserSearchCriteria none() {
        return new UserSearchCriteria(null, null, null);
    }
}
