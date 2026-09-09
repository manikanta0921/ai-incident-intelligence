package com.example.incident.controller;

import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.incident.dto.common.PagedResponse;
import com.example.incident.dto.user.RoleUpdateRequest;
import com.example.incident.dto.user.UserResponse;
import com.example.incident.exception.ResourceNotFoundException;
import com.example.incident.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * ADMIN-only user management (path also protected in SecurityConfig:
 * /api/users/** requires ROLE_ADMIN; @PreAuthorize adds defense in depth).
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User management endpoints (ADMIN only)")
@PreAuthorize("hasRole('ADMIN')") // method-level security for every handler below
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List users (paginated)")
    public PagedResponse<UserResponse> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return PagedResponse.of(userService.getAllUsers(parsePageable(page, size, sort)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by id")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return wrap(id, userService.getUserById(id));
    }

    @GetMapping("/by-email")
    @Operation(summary = "Get a user by email")
    public ResponseEntity<UserResponse> getUserByEmail(@RequestParam String email) {
        return userService.getUserByEmail(email)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Change a user's role",
            description = "Revokes the user's refresh tokens so old roles stop being issued")
    public ResponseEntity<UserResponse> updateRole(@PathVariable Long id,
                                                   @Valid @RequestBody RoleUpdateRequest request) {
        return ResponseEntity.ok(userService.updateRole(id, request.role()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user", description = "Fails if this would remove the last ADMIN")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- helpers ----------

    private ResponseEntity<UserResponse> wrap(Long id, Optional<UserResponse> user) {
        return user.map(ResponseEntity::ok)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    static Pageable parsePageable(int page, int size, String sort) {
        String[] parts = sort.split(",");
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(page, Math.min(size, 100), Sort.by(direction, parts[0]));
    }
}
