package com.itsectest.user.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.itsectest.shared.security.Role;
import com.itsectest.user.domain.User;
import com.itsectest.user.domain.UserRepository;
import com.itsectest.user.domain.UserSearchCriteria;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class UserJpaAdapter implements UserRepository {

    private final UserJpaRepository jpa;

    @Override
    public User save(User user) {
        return jpa.save(user);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsernameIgnoreCaseAndDeletedAtIsNull(username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpa.existsByUsernameIgnoreCaseAndDeletedAtIsNull(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmailIgnoreCaseAndDeletedAtIsNull(email);
    }

    // deleted users are included so their articles still show the author's username
    @Override
    public List<User> findAllById(Collection<UUID> ids) {
        return jpa.findAllById(ids);
    }

    @Override
    public Page<User> search(UserSearchCriteria criteria, Pageable pageable) {
        return jpa.findAll(UserSpecifications.matching(criteria), pageable);
    }

    @Override
    public long countByRole(Role role) {
        return jpa.countByRoleAndDeletedAtIsNull(role);
    }
}
