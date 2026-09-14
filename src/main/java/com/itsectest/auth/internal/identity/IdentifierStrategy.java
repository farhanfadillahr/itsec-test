package com.itsectest.auth.internal.identity;

import java.util.Optional;

import com.itsectest.user.api.UserAccount;

public interface IdentifierStrategy {

    boolean supports(String identifier);

    Optional<UserAccount> resolve(String identifier);

    String kind();
}
