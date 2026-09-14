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
        return jpa.findById(id);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsernameIgnoreCase(username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmailIgnoreCase(email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpa.existsByUsernameIgnoreCase(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmailIgnoreCase(email);
    }

    @Override
    public List<User> findAllById(Collection<UUID> ids) {
        return jpa.findAllById(ids);
    }

    @Override
    public Page<User> search(UserSearchCriteria criteria, Pageable pageable) {
        return jpa.findAll(UserSpecifications.matching(criteria), pageable);
    }

    @Override
    public void delete(User user) {
        jpa.delete(user);
    }

    @Override
    public long countByRole(Role role) {
        return jpa.countByRole(role);
    }
}
