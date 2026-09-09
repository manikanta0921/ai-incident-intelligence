package com.example.incident.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.incident.dto.user.UserResponse;
import com.example.incident.entity.User;
import com.example.incident.exception.BusinessException;
import com.example.incident.exception.ResourceNotFoundException;
import com.example.incident.repository.UserRepository;
import com.example.incident.service.impl.UserServiceImpl;

/**
 * Authorization-related business rules of user management.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.example.incident.service.AuthService authService;

    @InjectMocks
    private UserServiceImpl userService;

    private User admin;
    private User secondAdmin;
    private User customer;

    @BeforeEach
    void setUp() {
        admin = User.builder().id(1L).name("Admin").email("admin@example.com")
                .password("x").role(User.Role.ADMIN).build();
        secondAdmin = User.builder().id(2L).name("Admin2").email("admin2@example.com")
                .password("x").role(User.Role.ADMIN).build();
        customer = User.builder().id(3L).name("Customer").email("customer@example.com")
                .password("x").role(User.Role.CUSTOMER).build();
    }

    @Test
    @DisplayName("changing an existing user's role revokes their sessions")
    void updateRoleRevokesSessions() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(customer));

        UserResponse response = userService.updateRole(3L, User.Role.AGENT);

        assertThat(response.role()).isEqualTo(User.Role.AGENT);
        verify(authService).revokeAllSessions(3L);
    }

    @Test
    @DisplayName("cannot demote the last remaining ADMIN")
    void cannotDemoteLastAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findAll()).thenReturn(List.of(admin, customer)); // only one ADMIN exists

        assertThatThrownBy(() -> userService.updateRole(1L, User.Role.CUSTOMER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("last remaining ADMIN");
        verify(authService, never()).revokeAllSessions(1L);
    }

    @Test
    @DisplayName("can demote an ADMIN when another ADMIN exists")
    void canDemoteWhenOtherAdminsExist() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findAll()).thenReturn(List.of(admin, secondAdmin, customer));

        UserResponse response = userService.updateRole(1L, User.Role.AGENT);

        assertThat(response.role()).isEqualTo(User.Role.AGENT);
    }

    @Test
    @DisplayName("cannot delete the last remaining ADMIN")
    void cannotDeleteLastAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findAll()).thenReturn(List.of(admin, customer));

        assertThatThrownBy(() -> userService.deleteUser(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("last remaining ADMIN");
        verify(userRepository, never()).delete(admin);
    }

    @Test
    @DisplayName("deleting a normal user works and revokes their sessions")
    void deleteUser() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(customer));

        userService.deleteUser(3L);

        verify(userRepository).delete(customer);
        verify(authService).revokeAllSessions(3L);
    }

    @Test
    @DisplayName("unknown user id throws ResourceNotFoundException")
    void unknownUserThrows() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateRole(404L, User.Role.AGENT))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id: 404");
    }
}
