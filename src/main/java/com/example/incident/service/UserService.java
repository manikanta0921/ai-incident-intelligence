package com.example.incident.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.incident.dto.user.UserResponse;

/**
 * User management contract (ADMIN-facing).
 */
public interface UserService {

    Page<UserResponse> getAllUsers(Pageable pageable);

    Optional<UserResponse> getUserById(Long id);

    Optional<UserResponse> getUserByEmail(String email);

    /** Changes a user's role; deleting the last ADMIN is prevented. */
    UserResponse updateRole(Long userId, com.example.incident.entity.User.Role newRole);

    void deleteUser(Long userId);
}
