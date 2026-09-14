package com.itsectest.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.itsectest.shared.security.AuthPrincipal;
import com.itsectest.shared.security.CurrentUserProvider;
import com.itsectest.shared.security.Role;
import com.itsectest.user.api.UserAccount;
import com.itsectest.user.domain.UserSearchCriteria;
import com.itsectest.user.domain.UserStatus;
import com.itsectest.user.internal.CreateUserCommand;
import com.itsectest.user.internal.UpdateUserCommand;
import com.itsectest.user.internal.UserService;
import com.itsectest.user.web.dto.CreateUserRequest;
import com.itsectest.user.web.dto.UpdateUserRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Mock private UserService users;
    @Mock private CurrentUserProvider currentUser;

    @InjectMocks private UserController controller;

    private static UserAccount account() {
        return new UserAccount(USER_ID, "Farhan", "farhan", "farhan@example.com",
                Role.EDITOR, UserStatus.ACTIVE, true, null);
    }

    @Test
    void creatingAUserAnswersWithCreated() {
        when(users.create(any())).thenReturn(account());

        var response = controller.create(new CreateUserRequest(
                "Farhan", "farhan", "farhan@example.com", "Str0ng#Pass", Role.EDITOR, true));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().role()).isEqualTo(Role.EDITOR);
    }

    @Test
    void passesTheRequestedRoleThroughToTheService() {
        when(users.create(any())).thenReturn(account());

        controller.create(new CreateUserRequest(
                "Farhan", "farhan", "farhan@example.com", "Str0ng#Pass", Role.CONTRIBUTOR, false));

        ArgumentCaptor<CreateUserCommand> command = ArgumentCaptor.captor();
        verify(users).create(command.capture());
        assertThat(command.getValue().role()).isEqualTo(Role.CONTRIBUTOR);
        assertThat(command.getValue().mfaEnabled()).isFalse();
    }

    @Test
    void theListingPassesTheFiltersAlong() {
        when(users.search(any(), any())).thenReturn(new PageImpl<>(List.of()));

        controller.list("far", Role.EDITOR, UserStatus.ACTIVE, 0, 20, "createdAt", "desc");

        ArgumentCaptor<UserSearchCriteria> criteria = ArgumentCaptor.captor();
        verify(users).search(criteria.capture(), any(Pageable.class));
        assertThat(criteria.getValue().keyword()).isEqualTo("far");
        assertThat(criteria.getValue().role()).isEqualTo(Role.EDITOR);
        assertThat(criteria.getValue().status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void readingAUserReturnsIt() {
        when(users.get(USER_ID)).thenReturn(account());

        assertThat(controller.get(USER_ID).data().username()).isEqualTo("farhan");
    }

    @Test
    void updatingForwardsEveryMutableField() {
        when(users.update(eq(USER_ID), any())).thenReturn(account());

        controller.update(USER_ID, new UpdateUserRequest(
                "New Name", "new@example.com", Role.VIEWER, UserStatus.DISABLED, false, "Newp@ss1"));

        ArgumentCaptor<UpdateUserCommand> command = ArgumentCaptor.captor();
        verify(users).update(eq(USER_ID), command.capture());
        assertThat(command.getValue().fullname()).isEqualTo("New Name");
        assertThat(command.getValue().status()).isEqualTo(UserStatus.DISABLED);
        assertThat(command.getValue().rawPassword()).isEqualTo("Newp@ss1");
    }

    @Test
    void deletingTellsTheServiceWhoIsAskingSoSelfDeletionCanBeRefused() {
        when(currentUser.require()).thenReturn(new AuthPrincipal(ADMIN_ID, "root", Role.SUPER_ADMIN));

        assertThat(controller.delete(USER_ID).success()).isTrue();
        verify(users).delete(USER_ID, ADMIN_ID);
    }

    @Test
    void emptyCriteriaFilterNothing() {
        assertThat(UserSearchCriteria.none().keyword()).isNull();
        assertThat(UserSearchCriteria.none().role()).isNull();
        assertThat(UserSearchCriteria.none().status()).isNull();
    }
}
