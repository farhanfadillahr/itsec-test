package com.itsectest.auth.internal.identity;

import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.itsectest.user.api.UserAccount;
import com.itsectest.user.api.UserFacade;

import lombok.RequiredArgsConstructor;

@Component
@Order(10)
@RequiredArgsConstructor
public class EmailIdentifierStrategy implements IdentifierStrategy {

    private static final Pattern EMAIL_SHAPED = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserFacade users;

    @Override
    public boolean supports(String identifier) {
        return identifier != null && EMAIL_SHAPED.matcher(identifier.trim()).matches();
    }

    @Override
    public Optional<UserAccount> resolve(String identifier) {
        return users.findByEmail(identifier.trim());
    }

    @Override
    public String kind() {
        return "EMAIL";
    }
}
