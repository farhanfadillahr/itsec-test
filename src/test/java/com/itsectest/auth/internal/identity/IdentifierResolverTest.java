package com.itsectest.auth.internal.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.itsectest.shared.security.Role;
import com.itsectest.user.api.UserAccount;
import com.itsectest.user.api.UserFacade;
import com.itsectest.user.domain.UserStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdentifierResolverTest {

    @Mock private UserFacade users;

    private static UserAccount account() {
        return new UserAccount(UUID.randomUUID(), "Farhan", "farhan", "farhan@example.com",
                Role.VIEWER, UserStatus.ACTIVE, true, null);
    }

    private IdentifierResolver resolver() {
        return new IdentifierResolver(List.of(
                new EmailIdentifierStrategy(users), new UsernameIdentifierStrategy(users)));
    }

    @Test
    void looksUpAnEmailShapedIdentifierByEmail() {
        when(users.findByEmail("farhan@example.com")).thenReturn(Optional.of(account()));

        assertThat(resolver().resolve(" farhan@example.com ")).isPresent();
        assertThat(resolver().kindOf("farhan@example.com")).isEqualTo("EMAIL");
        verify(users, never()).findByUsername("farhan@example.com");
    }

    @Test
    void treatsAnythingElseAsAUsername() {
        when(users.findByUsername("farhan")).thenReturn(Optional.of(account()));

        assertThat(resolver().resolve("farhan")).isPresent();
        assertThat(resolver().kindOf("farhan")).isEqualTo("USERNAME");
    }

    @Test
    void doesNotMistakeAnIncompleteAddressForAnEmail() {
        EmailIdentifierStrategy email = new EmailIdentifierStrategy(users);

        assertThat(email.supports("farhan@example")).isFalse();
        assertThat(email.supports("farhan@")).isFalse();
        assertThat(email.supports("farhan")).isFalse();
        assertThat(email.supports(null)).isFalse();
        assertThat(email.supports("farhan@example.com")).isTrue();
    }

    @Test
    void reportsAnUnusableIdentifier() {
        assertThat(resolver().resolve("   ")).isEmpty();
        assertThat(resolver().kindOf("   ")).isEqualTo("UNKNOWN");
        assertThat(new UsernameIdentifierStrategy(users).supports(null)).isFalse();
    }
}
