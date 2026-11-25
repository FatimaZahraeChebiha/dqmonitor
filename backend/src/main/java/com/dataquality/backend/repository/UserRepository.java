package com.dataquality.backend.repository;

import com.dataquality.backend.model.Role;
import com.dataquality.backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Page<User> findAllByOrderByCreatedAtAsc(Pageable pageable);
    long countByRole(Role role);
}
