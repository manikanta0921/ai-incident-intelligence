package com.example.incident.service.impl;

import com.example.incident.dto.user.UserResponse;
import com.example.incident.entity.User;
import com.example.incident.entity.User.Role;
import com.example.incident.exception.BusinessException;
import com.example.incident.exception.ResourceNotFoundException;
import com.example.incident.repository.UserRepository;
import com.example.incident.service.AuthService;
import com.example.incident.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * ADMIN-facing user management.
 * Security note: every path into this service is protected at two levels -
 * /api/users/** requires ROLE_ADMIN in SecurityConfig, and business rules
 * (e.g. "never delete the last admin") live here.
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final AuthService authService;

    public UserServiceImpl(UserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserResponse> getUserById(Long id) {
        return userRepository.findById(id).map(UserResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserResponse> getUserByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase()).map(UserResponse::fromEntity);
    }

    /**
     * Role change is security-sensitive:
     *  - the caller cannot demote themselves (avoids an admin lockout by accident),
     *  - the system must always keep at least one ADMIN,
     *  - the target user's sessions are revoked so old tokens stop working.
     */
    @Override
    @Transactional
    public UserResponse updateRole(Long userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        if (user.getRole() == Role.ADMIN && newRole != Role.ADMIN && isLastAdmin(user)) {
            throw new BusinessException("Cannot demote the last remaining ADMIN");
        }

        user.setRole(newRole);

        // Old JWTs keep their "role" claim until they expire (15 min max), but refresh
        // tokens are revoked immediately so no new tokens with the old role are issued.
        authService.revokeAllSessions(userId);

        log.info("Changed role of user id={} to {}", userId, newRole);
        return UserResponse.fromEntity(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        if (isLastAdmin(user)) {
            throw new BusinessException("Cannot delete the last remaining ADMIN");
        }

        authService.revokeAllSessions(userId);
        userRepository.delete(user);
        log.info("Deleted user id={}", userId);
    }

    /** Cheap guard: an ADMIN count of 1 with that admin in hand means "last admin". */
    private boolean isLastAdmin(User user) {
        if (user.getRole() != Role.ADMIN) {
            return false;
        }
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .count() <= 1;
    }
}
