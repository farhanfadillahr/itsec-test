package com.itsectest.user.api;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.itsectest.shared.security.Role;

public interface UserFacade {

    UserAccount register(RegisterUserCommand command);

    Optional<UserAccount> findById(UUID id);

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByEmail(String email);

    boolean matchesPassword(UUID userId, String rawPassword);

    void lockUntil(UUID userId, Instant until);

    void clearLock(UUID userId);

    Role roleOf(UUID userId);

    Map<UUID, String> usernamesOf(Collection<UUID> userIds);
}
