package com.itsectest.auth.internal.identity;

import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.itsectest.user.api.UserAccount;
import com.itsectest.user.api.UserFacade;

import lombok.RequiredArgsConstructor;

@Component
@Order(20)
@RequiredArgsConstructor
public class UsernameIdentifierStrategy implements IdentifierStrategy {

    private final UserFacade users;

    @Override
    public boolean supports(String identifier) {
        return identifier != null && !identifier.isBlank();
    }

    @Override
    public Optional<UserAccount> resolve(String identifier) {
        return users.findByUsername(identifier.trim());
    }

    @Override
    public String kind() {
        return "USERNAME";
    }
}
