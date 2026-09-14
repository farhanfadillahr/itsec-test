package com.itsectest.user.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.itsectest.shared.error.ConflictException;
import com.itsectest.shared.error.ForbiddenException;
import com.itsectest.shared.error.NotFoundException;
import com.itsectest.shared.security.Role;
import com.itsectest.user.api.RegisterUserCommand;
import com.itsectest.user.api.UserAccount;
import com.itsectest.user.domain.User;
import com.itsectest.user.domain.UserRepository;
import com.itsectest.user.domain.UserSearchCriteria;
import com.itsectest.user.domain.UserStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private UserRepository users;
    @Mock private PasswordEncoder passwordEncoder;

    private UserService service;

    private static User user(Role role, UserStatus status) {
        return User.builder()
                .id(USER_ID).fullname("Farhan").username("farhan").email("farhan@example.com")
                .password("$2a$12$hash").role(role).status(status).mfaEnabled(true)
                .build();
    }

    @BeforeEach
    void setUp() {
        service = new UserService(users, passwordEncoder);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hash");
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void registrationAlwaysProducesAViewer() {
        UserAccount account = service.register(
                new RegisterUserCommand("Farhan", "farhan", "Farhan@Example.COM", "Str0ng#Pass"));

        assertThat(account.role()).isEqualTo(Role.VIEWER);
        assertThat(account.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(account.mfaEnabled()).isTrue();
    }

    @Test
    void normalisesTheEmailAndTrimsTheName() {
        service.register(new RegisterUserCommand("  Farhan  ", " farhan ", " Farhan@Example.COM ", "Str0ng#Pass"));

        ArgumentCaptor<User> saved = ArgumentCaptor.captor();
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("farhan@example.com");
        assertThat(saved.getValue().getFullname()).isEqualTo("Farhan");
        assertThat(saved.getValue().getUsername()).isEqualTo("farhan");
    }

    @Test
    void neverStoresThePasswordInClear() {
        service.register(new RegisterUserCommand("Farhan", "farhan", "f@example.com", "Str0ng#Pass"));

        ArgumentCaptor<User> saved = ArgumentCaptor.captor();
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isNotEqualTo("Str0ng#Pass").startsWith("$2a$");
    }

    @Test
    void refusesATakenUsername() {
        when(users.existsByUsername("farhan")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand("Farhan", "farhan", "f@example.com", "Str0ng#Pass")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username is already taken");
    }

    @Test
    void refusesATakenEmail() {
        when(users.existsByEmail("f@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand("Farhan", "farhan", "f@example.com", "Str0ng#Pass")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email is already registered");
    }

    @Test
    void anAdministratorMayCreateAnAccountWithAnExplicitRole() {
        UserAccount created = service.create(new CreateUserCommand(
                "Editor", "editor", "editor@example.com", "Str0ng#Pass", Role.EDITOR, false));

        assertThat(created.role()).isEqualTo(Role.EDITOR);
        assertThat(created.mfaEnabled()).isFalse();
    }

    @Test
    void looksUpAccountsByIdUsernameAndEmail() {
        when(users.findById(USER_ID)).thenReturn(Optional.of(user(Role.EDITOR, UserStatus.ACTIVE)));
        when(users.findByUsername("farhan")).thenReturn(Optional.of(user(Role.EDITOR, UserStatus.ACTIVE)));
        when(users.findByEmail("farhan@example.com")).thenReturn(Optional.of(user(Role.EDITOR, UserStatus.ACTIVE)));

        assertThat(service.findById(USER_ID)).isPresent();
        assertThat(service.findByUsername("farhan")).isPresent();
        assertThat(service.findByEmail("farhan@example.com")).isPresent();
        assertThat(service.roleOf(USER_ID)).isEqualTo(Role.EDITOR);
    }

    @Test
    void verifiesPasswordsWithoutEverExposingTheHash() {
        when(users.findById(USER_ID)).thenReturn(Optional.of(user(Role.VIEWER, UserStatus.ACTIVE)));
        when(passwordEncoder.matches("right", "$2a$12$hash")).thenReturn(true);

        assertThat(service.matchesPassword(USER_ID, "right")).isTrue();
        assertThat(service.matchesPassword(USER_ID, "wrong")).isFalse();
    }

    @Test
    void treatsAMissingAccountAsANonMatchingPassword() {
        when(users.findById(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.matchesPassword(USER_ID, "anything")).isFalse();
    }

    @Test
    void locksAndUnlocksAnAccount() {
        User account = user(Role.VIEWER, UserStatus.ACTIVE);
        when(users.findById(USER_ID)).thenReturn(Optional.of(account));
        Instant until = Instant.now().plusSeconds(1800);

        service.lockUntil(USER_ID, until);
        assertThat(account.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(account.getLockedUntil()).isEqualTo(until);
        assertThat(account.isLocked()).isTrue();

        service.clearLock(USER_ID);
        assertThat(account.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(account.getLockedUntil()).isNull();
    }

    @Test
    void lockingAVanishedAccountIsANoOp() {
        when(users.findById(USER_ID)).thenReturn(Optional.empty());

        service.lockUntil(USER_ID, Instant.now());
        service.clearLock(USER_ID);

        verify(users, never()).save(any());
    }

    @Test
    void resolvesManyUsernamesInOneQuery() {
        UUID other = UUID.randomUUID();
        User second = User.builder().id(other).fullname("B").username("bob").email("b@example.com")
                .password("x").role(Role.VIEWER).status(UserStatus.ACTIVE).build();
        when(users.findAllById(any())).thenReturn(List.of(user(Role.EDITOR, UserStatus.ACTIVE), second));

        assertThat(service.usernamesOf(List.of(USER_ID, other)))
                .containsEntry(USER_ID, "farhan")
                .containsEntry(other, "bob");
    }

    @Test
    void asksForNothingWhenThereAreNoAuthorsToResolve() {
        assertThat(service.usernamesOf(List.of())).isEmpty();
        assertThat(service.usernamesOf(null)).isEmpty();
        verify(users, never()).findAllById(any());
    }

    @Test
    void readsAndSearchesAccounts() {
        when(users.findById(USER_ID)).thenReturn(Optional.of(user(Role.EDITOR, UserStatus.ACTIVE)));
        when(users.search(any(), any())).thenReturn(
                new PageImpl<>(List.of(user(Role.EDITOR, UserStatus.ACTIVE))));

        assertThat(service.get(USER_ID).username()).isEqualTo("farhan");
        assertThat(service.search(UserSearchCriteria.none(), PageRequest.of(0, 20))).hasSize(1);
    }

    @Test
    void reportsAMissingAccountAsNotFound() {
        when(users.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatesTheMutableFields() {
        User account = user(Role.VIEWER, UserStatus.ACTIVE);
        when(users.findById(USER_ID)).thenReturn(Optional.of(account));

        UserAccount updated = service.update(USER_ID, new UpdateUserCommand(
                "New Name", "NEW@example.com", Role.EDITOR, UserStatus.ACTIVE, false, null));

        assertThat(updated.fullname()).isEqualTo("New Name");
        assertThat(updated.email()).isEqualTo("new@example.com");
        assertThat(updated.role()).isEqualTo(Role.EDITOR);
        assertThat(updated.mfaEnabled()).isFalse();
    }

    @Test
    void keepsTheExistingPasswordWhenNoneIsSupplied() {
        User account = user(Role.VIEWER, UserStatus.ACTIVE);
        when(users.findById(USER_ID)).thenReturn(Optional.of(account));

        service.update(USER_ID, new UpdateUserCommand(
                "Name", "f@example.com", Role.VIEWER, UserStatus.ACTIVE, true, "   "));

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void rehashesWhenANewPasswordIsSupplied() {
        User account = user(Role.VIEWER, UserStatus.ACTIVE);
        when(users.findById(USER_ID)).thenReturn(Optional.of(account));

        service.update(USER_ID, new UpdateUserCommand(
                "Name", "f@example.com", Role.VIEWER, UserStatus.ACTIVE, true, "Newp@ss1"));

        verify(passwordEncoder).encode("Newp@ss1");
    }

    @Test
    void refusesToMoveAnEmailOntoAnAccountThatAlreadyHasIt() {
        when(users.findById(USER_ID)).thenReturn(Optional.of(user(Role.VIEWER, UserStatus.ACTIVE)));
        when(users.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.update(USER_ID, new UpdateUserCommand(
                "Name", "taken@example.com", Role.VIEWER, UserStatus.ACTIVE, true, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void refusesToDemoteTheLastSuperAdmin() {
        when(users.findById(USER_ID)).thenReturn(Optional.of(user(Role.SUPER_ADMIN, UserStatus.ACTIVE)));
        when(users.countByRole(Role.SUPER_ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.update(USER_ID, new UpdateUserCommand(
                "Name", "farhan@example.com", Role.EDITOR, UserStatus.ACTIVE, true, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("last super admin");
    }

    @Test
    void allowsDemotingASuperAdminWhileAnotherRemains() {
        when(users.findById(USER_ID)).thenReturn(Optional.of(user(Role.SUPER_ADMIN, UserStatus.ACTIVE)));
        when(users.countByRole(Role.SUPER_ADMIN)).thenReturn(2L);

        assertThat(service.update(USER_ID, new UpdateUserCommand(
                "Name", "farhan@example.com", Role.EDITOR, UserStatus.ACTIVE, true, null)).role())
                .isEqualTo(Role.EDITOR);
    }

    @Test
    void clearsAStaleLockWhenTheStatusIsNoLongerLocked() {
        User account = user(Role.VIEWER, UserStatus.LOCKED);
        account.setLockedUntil(Instant.now().plusSeconds(600));
        when(users.findById(USER_ID)).thenReturn(Optional.of(account));

        service.update(USER_ID, new UpdateUserCommand(
                "Name", "farhan@example.com", Role.VIEWER, UserStatus.ACTIVE, true, null));

        assertThat(account.getLockedUntil()).isNull();
    }

    @Test
    void deletesAnAccount() {
        User account = user(Role.EDITOR, UserStatus.ACTIVE);
        when(users.findById(USER_ID)).thenReturn(Optional.of(account));

        service.delete(USER_ID, UUID.randomUUID());

        verify(users).delete(account);
    }

    @Test
    void refusesToLetAnAdministratorDeleteThemselves() {
        when(users.findById(USER_ID)).thenReturn(Optional.of(user(Role.SUPER_ADMIN, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.delete(USER_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("your own account");
    }

    @Test
    void refusesToDeleteTheLastSuperAdmin() {
        when(users.findById(USER_ID)).thenReturn(Optional.of(user(Role.SUPER_ADMIN, UserStatus.ACTIVE)));
        when(users.countByRole(Role.SUPER_ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(USER_ID, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void reportsAMissingAccountOnDelete() {
        when(users.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void entityHelpersBehave() {
        User account = user(Role.VIEWER, UserStatus.LOCKED);
        account.setLockedUntil(Instant.now().minusSeconds(60));

        assertThat(account.isLocked()).isFalse();
        assertThat(account.isUsable()).isTrue();

        account.releaseExpiredLock();
        assertThat(account.getStatus()).isEqualTo(UserStatus.ACTIVE);

        User disabled = user(Role.VIEWER, UserStatus.DISABLED);
        assertThat(disabled.isUsable()).isFalse();
        assertThat(disabled).isEqualTo(user(Role.VIEWER, UserStatus.ACTIVE));
        assertThat(disabled).isNotEqualTo("not a user");
        assertThat(disabled.hashCode()).isEqualTo(USER_ID.hashCode());
    }
}
