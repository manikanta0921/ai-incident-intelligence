package com.example.incident.repository;

import com.example.incident.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Login and duplicate-email check both look users up by email. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
