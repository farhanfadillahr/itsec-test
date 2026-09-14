package com.itsectest.user.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findAllById(Collection<UUID> ids);

    Page<User> search(UserSearchCriteria criteria, Pageable pageable);

    void delete(User user);

    long countByRole(com.itsectest.shared.security.Role role);
}
