package com.itsectest.user.web;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itsectest.shared.security.CurrentUserProvider;
import com.itsectest.shared.security.Role;
import com.itsectest.shared.web.ApiResponse;
import com.itsectest.shared.web.PageResponse;
import com.itsectest.shared.web.PageableFactory;
import com.itsectest.user.api.UserAccount;
import com.itsectest.user.domain.UserSearchCriteria;
import com.itsectest.user.domain.UserStatus;
import com.itsectest.user.internal.CreateUserCommand;
import com.itsectest.user.internal.UpdateUserCommand;
import com.itsectest.user.internal.UserService;
import com.itsectest.user.web.dto.CreateUserRequest;
import com.itsectest.user.web.dto.UpdateUserRequest;
import com.itsectest.user.web.dto.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@Validated
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management, super admin only")
public class UserController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "username", "email", "fullname");

    private final UserService users;
    private final CurrentUserProvider currentUser;

    @PostMapping
    @Operation(summary = "Create a user", description = "Creates an account with an explicit role.")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        UserAccount created = users.create(new CreateUserCommand(
                request.fullname(), request.username(), request.email(),
                request.password(), request.role(), request.mfaEnabled()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("User created", UserResponse.from(created)));
    }

    @GetMapping
    @Operation(summary = "List users", description = "Paged, with optional keyword, role and status filters.")
    public ApiResponse<PageResponse<UserResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Pageable pageable = PageableFactory.of(page, size, sortBy, direction, SORTABLE);
        return ApiResponse.of("Users retrieved", PageResponse.from(
                users.search(new UserSearchCriteria(keyword, role, status), pageable),
                UserResponse::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user")
    public ApiResponse<UserResponse> get(@PathVariable UUID id) {
        return ApiResponse.of("User retrieved", UserResponse.from(users.get(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user",
            description = "Replaces the mutable fields. Omit `password` to keep the current one.")
    public ApiResponse<UserResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {

        UserAccount updated = users.update(id, new UpdateUserCommand(
                request.fullname(), request.email(), request.role(),
                request.status(), request.mfaEnabled(), request.password()));

        return ApiResponse.of("User updated", UserResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user",
            description = "Refuses to delete the caller's own account or the last remaining super admin.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        users.delete(id, currentUser.require().userId());
        return ApiResponse.message("User deleted");
    }
}
