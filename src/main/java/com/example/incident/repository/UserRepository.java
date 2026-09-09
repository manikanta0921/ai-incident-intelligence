package com.example.incident.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.incident.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Login and duplicate-email check both look users up by email. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
