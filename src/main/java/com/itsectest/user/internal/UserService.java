package com.itsectest.user.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itsectest.shared.audit.api.AuditAction;
import com.itsectest.shared.audit.api.Auditable;
import com.itsectest.shared.error.ConflictException;
import com.itsectest.shared.error.ForbiddenException;
import com.itsectest.shared.error.NotFoundException;
import com.itsectest.shared.security.Role;
import com.itsectest.user.api.RegisterUserCommand;
import com.itsectest.user.api.UserAccount;
import com.itsectest.user.api.UserFacade;
import com.itsectest.user.domain.User;
import com.itsectest.user.domain.UserRepository;
import com.itsectest.user.domain.UserSearchCriteria;
import com.itsectest.user.domain.UserStatus;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService implements UserFacade {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Auditable(action = AuditAction.REGISTER, resourceType = "USER", resourceId = "#result.id()")
    public UserAccount register(RegisterUserCommand command) {
        requireAvailable(command.username(), command.email());

        User user = User.builder()
                .fullname(command.fullname().trim())
                .username(command.username().trim())
                .email(command.email().trim().toLowerCase())
                .password(passwordEncoder.encode(command.rawPassword()))
                .role(Role.VIEWER)
                .status(UserStatus.ACTIVE)
                .mfaEnabled(true)
                .build();

        return toAccount(users.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findById(UUID id) {
        return users.findById(id).map(UserService::toAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByUsername(String username) {
        return users.findByUsername(username).map(UserService::toAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByEmail(String email) {
        return users.findByEmail(email).map(UserService::toAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean matchesPassword(UUID userId, String rawPassword) {
        return users.findById(userId)
                .map(user -> passwordEncoder.matches(rawPassword, user.getPassword()))
                .orElse(false);
    }

    @Override
    public void lockUntil(UUID userId, Instant until) {
        users.findById(userId).ifPresent(user -> {
            user.setStatus(UserStatus.LOCKED);
            user.setLockedUntil(until);
            users.save(user);
        });
    }

    @Override
    public void clearLock(UUID userId) {
        users.findById(userId).ifPresent(user -> {
            user.setStatus(UserStatus.ACTIVE);
            user.setLockedUntil(null);
            users.save(user);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Role roleOf(UUID userId) {
        return require(userId).getRole();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> usernamesOf(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (first, second) -> first));
    }

    @Auditable(action = AuditAction.USER_CREATED, resourceType = "USER", resourceId = "#result.id()")
    public UserAccount create(CreateUserCommand command) {
        requireAvailable(command.username(), command.email());

        User user = User.builder()
                .fullname(command.fullname().trim())
                .username(command.username().trim())
                .email(command.email().trim().toLowerCase())
                .password(passwordEncoder.encode(command.rawPassword()))
                .role(command.role())
                .status(UserStatus.ACTIVE)
                .mfaEnabled(command.mfaEnabled())
                .build();

        return toAccount(users.save(user));
    }

    @Transactional(readOnly = true)
    @Auditable(action = AuditAction.USER_VIEWED, resourceType = "USER", resourceId = "#id")
    public UserAccount get(UUID id) {
        return toAccount(require(id));
    }

    @Transactional(readOnly = true)
    @Auditable(action = AuditAction.USER_LIST_VIEWED, resourceType = "USER")
    public Page<UserAccount> search(UserSearchCriteria criteria, Pageable pageable) {
        return users.search(criteria, pageable).map(UserService::toAccount);
    }

    @Auditable(action = AuditAction.USER_UPDATED, resourceType = "USER", resourceId = "#id")
    public UserAccount update(UUID id, UpdateUserCommand command) {
        User user = require(id);

        String email = command.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }
        if (user.getRole() == Role.SUPER_ADMIN && command.role() != Role.SUPER_ADMIN) {
            requireAnotherSuperAdminExists();
        }

        user.setFullname(command.fullname().trim());
        user.setEmail(email);
        user.setRole(command.role());
        user.setStatus(command.status());
        user.setMfaEnabled(command.mfaEnabled());
        if (command.rawPassword() != null && !command.rawPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(command.rawPassword()));
        }
        if (command.status() != UserStatus.LOCKED) {
            user.setLockedUntil(null);
        }
        return toAccount(users.save(user));
    }

    @Auditable(action = AuditAction.USER_DELETED, resourceType = "USER", resourceId = "#id")
    public void delete(UUID id, UUID actorId) {
        User user = require(id);
        if (user.getId().equals(actorId)) {
            throw new ForbiddenException("You cannot delete your own account");
        }
        if (user.getRole() == Role.SUPER_ADMIN) {
            requireAnotherSuperAdminExists();
        }
        users.delete(user);
    }

    private User require(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private void requireAvailable(String username, String email) {
        if (users.existsByUsername(username.trim())) {
            throw new ConflictException("Username is already taken");
        }
        if (users.existsByEmail(email.trim())) {
            throw new ConflictException("Email is already registered");
        }
    }

    private void requireAnotherSuperAdminExists() {
        if (users.countByRole(Role.SUPER_ADMIN) <= 1) {
            throw new ConflictException("The last super admin cannot be removed or demoted");
        }
    }

    static UserAccount toAccount(User user) {
        return new UserAccount(
                user.getId(),
                user.getFullname(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.isMfaEnabled(),
                user.getLockedUntil());
    }
}
