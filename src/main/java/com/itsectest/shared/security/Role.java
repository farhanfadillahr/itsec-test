package com.itsectest.shared.security;

public enum Role {

    SUPER_ADMIN,

    EDITOR,

    CONTRIBUTOR,

    VIEWER;

    public String authority() {
        return "ROLE_" + name();
    }

    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }
}
