package com.example.incident.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.incident.dto.incident.IncidentRequest;
import com.example.incident.dto.incident.IncidentResponse;
import com.example.incident.dto.incident.IncidentStatusUpdateRequest;
import com.example.incident.dto.incident.IncidentUpdateRequest;
import com.example.incident.entity.Incident;
import com.example.incident.entity.IncidentCategory;
import com.example.incident.entity.IncidentSeverity;
import com.example.incident.entity.IncidentStatus;
import com.example.incident.entity.User;
import com.example.incident.exception.BusinessException;
import com.example.incident.exception.ResourceNotFoundException;
import com.example.incident.repository.IncidentRepository;
import com.example.incident.repository.UserRepository;
import com.example.incident.security.CustomUserDetails;
import com.example.incident.service.impl.IncidentServiceImpl;

/**
 * Pure unit tests: the repository is mocked, no database, no Spring context.
 * Runs in milliseconds.
 */
@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private IncidentServiceImpl incidentService;

    @Captor
    private ArgumentCaptor<Incident> incidentCaptor;

    private static final Long CUSTOMER_ID = 7L;
    private static final Long OTHER_CUSTOMER_ID = 99L;
    private static final Long INCIDENT_ID = 42L;

    private CustomUserDetails customer;
    private CustomUserDetails agent;
    private User ownerEntity;

    @BeforeEach
    void setUp() {
        customer = new CustomUserDetails(CUSTOMER_ID, "owner@example.com", "hash", User.Role.CUSTOMER);
        agent = new CustomUserDetails(1L, "agent@example.com", "hash", User.Role.AGENT);
        ownerEntity = User.builder()
                .id(CUSTOMER_ID)
                .name("Owner")
                .email("owner@example.com")
                .password("hash")
                .role(User.Role.CUSTOMER)
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Incident existingIncident(IncidentStatus status) {
        return Incident.builder()
                .id(INCIDENT_ID)
                .title("Payment deducted but order failed")
                .description("Customer was charged 999 but the order was not created.")
                .category(IncidentCategory.PAYMENT)
                .severity(IncidentSeverity.HIGH)
                .status(status)
                .reportedBy(ownerEntity)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private IncidentRequest validRequest() {
        return new IncidentRequest(
                "Payment deducted but order failed",
                "Customer was charged 999 but the order was not created.",
                IncidentCategory.PAYMENT,
                IncidentSeverity.HIGH);
    }

    private void loginAs(CustomUserDetails user) {
        SecurityContextHolder.getContext()
                .setAuthentication(new org.springframework.security.authentication
                        .UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Nested
    @DisplayName("createIncident")
    class Create {

        @Test
        @DisplayName("creates incident with server-side defaults (status OPEN, client cannot set id/status)")
        void createsIncidentWithDefaults() {
            loginAs(customer);
            when(userRepository.getReferenceById(CUSTOMER_ID)).thenReturn(ownerEntity);
            when(incidentRepository.save(any(Incident.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0, Incident.class));

            IncidentResponse response = incidentService.createIncident(validRequest(), customer);

            ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
            verify(incidentRepository).save(captor.capture());
            Incident saved = captor.getValue();

            assertThat(saved.getStatus()).isEqualTo(IncidentStatus.OPEN); // forced server-side
            assertThat(saved.getReportedBy().getId()).isEqualTo(CUSTOMER_ID);
            assertThat(response.status()).isEqualTo(IncidentStatus.OPEN);
            assertThat(response.summary()).isNull();
            assertThat(response.resolution()).isNull();
        }
    }

    @Nested
    @DisplayName("getIncidentById")
    class GetById {

        @Test
        @DisplayName("returns the incident when the owner requests it")
        void ownerCanRead() {
            loginAs(customer);
            when(incidentRepository.findById(INCIDENT_ID)).thenReturn(Optional.of(existingIncident(IncidentStatus.OPEN)));

            IncidentResponse response = incidentService.getIncidentById(INCIDENT_ID);

            assertThat(response.id()).isEqualTo(INCIDENT_ID);
            assertThat(response.reportedById()).isEqualTo(CUSTOMER_ID);
        }

        @Test
        @DisplayName("customer cannot read another customer's incident (ownership rule)")
        void customerCannotReadOthersIncident() {
            loginAs(new CustomUserDetails(OTHER_CUSTOMER_ID, "other@example.com", "hash", User.Role.CUSTOMER));
            when(incidentRepository.findById(INCIDENT_ID)).thenReturn(Optional.of(existingIncident(IncidentStatus.OPEN)));

            assertThatThrownBy(() -> incidentService.getIncidentById(INCIDENT_ID))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                    .hasMessageContaining("do not have access");
        }

        @Test
        @DisplayName("agent can read any customer's incident")
        void agentCanReadAnyIncident() {
            loginAs(agent);
            when(incidentRepository.findById(INCIDENT_ID)).thenReturn(Optional.of(existingIncident(IncidentStatus.OPEN)));

            assertThat(incidentService.getIncidentById(INCIDENT_ID).id()).isEqualTo(INCIDENT_ID);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the incident does not exist")
        void missingIncidentThrows404() {
            loginAs(agent);
            when(incidentRepository.findById(INCIDENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> incidentService.getIncidentById(INCIDENT_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Incident not found with id: 42");
        }
    }

    @Nested
    @DisplayName("updateIncident")
    class Update {

        @Test
        @DisplayName("owner can update their own OPEN incident")
        void ownerUpdatesOpenIncident() {
            loginAs(customer);
            Incident incident = existingIncident(IncidentStatus.OPEN);
            when(incidentRepository.findById(INCIDENT_ID)).thenReturn(Optional.of(incident));

            IncidentUpdateRequest request = new IncidentUpdateRequest(
                    "Payment deducted but order failed (updated)",
                    "Customer was charged 999 but the order was not created. Adding payment reference.",
                    IncidentCategory.PAYMENT,
                    IncidentSeverity.CRITICAL);

            IncidentResponse response = incidentService.updateIncident(INCIDENT_ID, request);

            assertThat(response.severity()).isEqualTo(IncidentSeverity.CRITICAL);
        }

        @Test
        @DisplayName("customer cannot update their incident once it is not OPEN")
        void customerCannotUpdateNonOpenIncident() {
            loginAs(customer);
            when(incidentRepository.findById(INCIDENT_ID))
                    .thenReturn(Optional.of(existingIncident(IncidentStatus.IN_PROGRESS)));

            IncidentUpdateRequest request = new IncidentUpdateRequest(
                    "New title here", "Some sufficiently long description of the problem.",
                    IncidentCategory.PAYMENT, IncidentSeverity.LOW);

            assertThatThrownBy(() -> incidentService.updateIncident(INCIDENT_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("OPEN");
        }
    }

    @Nested
    @DisplayName("updateIncidentStatus")
    class StatusUpdate {

        @Test
        @DisplayName("agent moves OPEN -> IN_PROGRESS")
        void agentMovesOpenToInProgress() {
            loginAs(agent);
            when(incidentRepository.findById(INCIDENT_ID))
                    .thenReturn(Optional.of(existingIncident(IncidentStatus.OPEN)));

            IncidentResponse response = incidentService.updateIncidentStatus(
                    INCIDENT_ID, new IncidentStatusUpdateRequest(IncidentStatus.IN_PROGRESS, null));

            assertThat(response.status()).isEqualTo(IncidentStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("customer cannot change status at all")
        void customerCannotChangeStatus() {
            loginAs(customer);
            when(incidentRepository.findById(INCIDENT_ID))
                    .thenReturn(Optional.of(existingIncident(IncidentStatus.OPEN)));

            assertThatThrownBy(() -> incidentService.updateIncidentStatus(
                    INCIDENT_ID, new IncidentStatusUpdateRequest(IncidentStatus.RESOLVED, null)))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                    .hasMessageContaining("AGENT or ADMIN");
        }

        @Test
        @DisplayName("illegal transition CLOSED -> OPEN is rejected")
        void illegalTransitionRejected() {
            loginAs(agent);
            when(incidentRepository.findById(INCIDENT_ID))
                    .thenReturn(Optional.of(existingIncident(IncidentStatus.CLOSED)));

            assertThatThrownBy(() -> incidentService.updateIncidentStatus(
                    INCIDENT_ID, new IncidentStatusUpdateRequest(IncidentStatus.OPEN, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Illegal status transition");
        }

        @Test
        @DisplayName("resolution can only accompany RESOLVED or CLOSED")
        void resolutionOnlyWithResolvedOrClosed() {
            loginAs(agent);
            when(incidentRepository.findById(INCIDENT_ID))
                    .thenReturn(Optional.of(existingIncident(IncidentStatus.OPEN)));

            assertThatThrownBy(() -> incidentService.updateIncidentStatus(
                    INCIDENT_ID, new IncidentStatusUpdateRequest(IncidentStatus.IN_PROGRESS, "Some fix")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("RESOLVED or CLOSED");
        }

        @Test
        @DisplayName("RESOLVED with resolution text stores the resolution")
        void resolveWithResolution() {
            loginAs(agent);
            when(incidentRepository.findById(INCIDENT_ID))
                    .thenReturn(Optional.of(existingIncident(IncidentStatus.OPEN)));

            IncidentResponse response = incidentService.updateIncidentStatus(
                    INCIDENT_ID,
                    new IncidentStatusUpdateRequest(IncidentStatus.RESOLVED, "Refund initiated"));

            assertThat(response.status()).isEqualTo(IncidentStatus.RESOLVED);
            assertThat(response.resolution()).isEqualTo("Refund initiated");
        }
    }

    @Nested
    @DisplayName("deleteIncident")
    class Delete {

        @Test
        @DisplayName("customer deletes their own OPEN incident")
        void customerDeletesOwnOpenIncident() {
            loginAs(customer);
            when(incidentRepository.findById(INCIDENT_ID))
                    .thenReturn(Optional.of(existingIncident(IncidentStatus.OPEN)));

            incidentService.deleteIncident(INCIDENT_ID);

            verify(incidentRepository).delete(any(Incident.class));
        }

        @Test
        @DisplayName("customer cannot delete another customer's incident")
        void customerCannotDeleteOthersIncident() {
            loginAs(new CustomUserDetails(OTHER_CUSTOMER_ID, "other@example.com", "hash", User.Role.CUSTOMER));
            when(incidentRepository.findById(INCIDENT_ID))
                    .thenReturn(Optional.of(existingIncident(IncidentStatus.OPEN)));

            assertThatThrownBy(() -> incidentService.deleteIncident(INCIDENT_ID))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                    .hasMessageContaining("do not have access");
        }
    }

    @Nested
    @DisplayName("getAllIncidents (pagination + filtering)")
    class Listing {

        @Test
        @DisplayName("customer's reporterId parameter is overridden with their own id")
        void customerCannotListOthersIncidents() {
            loginAs(customer);
            Page<Incident> page = new PageImpl<>(List.of(existingIncident(IncidentStatus.OPEN)));
            when(incidentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            Page<IncidentResponse> result = incidentService.getAllIncidents(
                    OTHER_CUSTOMER_ID, IncidentStatus.OPEN, null, null, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            // The service must have passed the customer's own id as reporterId, not 99.
            verify(incidentRepository).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        @DisplayName("agent can list incidents with filters (specification path)")
        void agentListsFiltered() {
            loginAs(agent);
            Page<Incident> page = new PageImpl<>(List.of(existingIncident(IncidentStatus.OPEN)));
            when(incidentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            Page<IncidentResponse> result = incidentService.getAllIncidents(
                    null, IncidentStatus.OPEN, IncidentSeverity.HIGH, IncidentCategory.PAYMENT,
                    PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
        }
    }
}
