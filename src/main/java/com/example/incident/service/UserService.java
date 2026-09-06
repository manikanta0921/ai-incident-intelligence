package com.example.incident.service;

import com.example.incident.dto.user.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

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
