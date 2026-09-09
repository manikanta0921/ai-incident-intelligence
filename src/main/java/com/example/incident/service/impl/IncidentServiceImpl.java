package com.example.incident.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.incident.dto.incident.IncidentRequest;
import com.example.incident.dto.incident.IncidentResponse;
import com.example.incident.dto.incident.IncidentStatusUpdateRequest;
import com.example.incident.dto.incident.IncidentUpdateRequest;
import com.example.incident.entity.Incident;
import com.example.incident.entity.IncidentCategory;
import com.example.incident.entity.IncidentSeverity;
import com.example.incident.entity.IncidentStatus;
import com.example.incident.entity.User;
import com.example.incident.entity.User.Role;
import com.example.incident.exception.BusinessException;
import com.example.incident.exception.ResourceNotFoundException;
import com.example.incident.repository.IncidentRepository;
import com.example.incident.repository.UserRepository;
import com.example.incident.security.CustomUserDetails;
import com.example.incident.service.IncidentService;
import com.example.incident.service.IncidentSpecifications;
import com.example.incident.util.SecurityUtils;

/**
 * All incident business rules live here. Controllers only translate HTTP;
 * repositories only talk to the database.
 */
@Service
public class IncidentServiceImpl implements IncidentService {

    private static final int MAX_PAGE_SIZE = 100;

    /** Whitelisted sort fields, mapped to entity properties (guards dynamic sorting). */
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "status", "status",
            "severity", "severity",
            "category", "category"
    );

    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;

    public IncidentServiceImpl(IncidentRepository incidentRepository, UserRepository userRepository) {
        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates an incident owned by the currently authenticated customer.
     * Server-side defaults: status = OPEN, summary/resolution = null,
     * createdAt/updatedAt are set by Hibernate. The client cannot set any of these.
     */
    @Override
    @Transactional
    public IncidentResponse createIncident(IncidentRequest request, CustomUserDetails currentUser) {
        User reporter = userRepository.getReferenceById(currentUser.getId());

        Incident incident = Incident.builder()
                .title(request.title())
                .description(request.description())
                .category(request.category())
                .severity(request.severity())
                .status(IncidentStatus.OPEN) // every incident starts as OPEN
                .reportedBy(reporter)
                .build();

        return IncidentResponse.fromEntity(incidentRepository.save(incident));
    }

    /**
     * Filtered, paginated listing.
     * CUSTOMER -> only their own incidents (owner filter is forced server-side).
     * AGENT/ADMIN -> all incidents (an optional reporterId filter can still be passed).
     */
    @Override
    @Transactional(readOnly = true)
    public Page<IncidentResponse> getAllIncidents(Long reporterId, IncidentStatus status,
                                                  IncidentSeverity severity, IncidentCategory category,
                                                  Pageable pageable) {
        Long effectiveReporterId = reporterId;
        if (SecurityUtils.getCurrentUser().hasRole(Role.CUSTOMER)) {
            effectiveReporterId = SecurityUtils.getCurrentUserId(); // clients cannot override this
        }

        Specification<Incident> spec = IncidentSpecifications.withFilters(
                effectiveReporterId, status, severity, category);
        Page<Incident> page = incidentRepository.findAll(spec, sanitize(pageable));
        return page.map(IncidentResponse::fromEntity);
    }

    /** Ownership rule: CUSTOMERs may only read their own incidents; AGENT/ADMIN read any. */
    @Override
    @Transactional(readOnly = true)
    public IncidentResponse getIncidentById(Long id) {
        Incident incident = findIncident(id);
        enforceOwnership(incident);
        return IncidentResponse.fromEntity(incident);
    }

    /**
     * Full update. CUSTOMERs may edit their own incidents only while they are still
     * OPEN (before an agent starts working). AGENT/ADMIN may edit any incident.
     */
    @Override
    @Transactional
    public IncidentResponse updateIncident(Long id, IncidentUpdateRequest request) {
        Incident incident = findIncident(id);
        enforceOwnership(incident);

        if (currentUserIsCustomerOnly() && incident.getStatus() != IncidentStatus.OPEN) {
            throw new BusinessException("Customers can only edit incidents while they are OPEN");
        }

        incident.setTitle(request.title());
        incident.setDescription(request.description());
        incident.setCategory(request.category());
        incident.setSeverity(request.severity());
        // updatedAt is refreshed automatically by Hibernate @UpdateTimestamp

        return IncidentResponse.fromEntity(incident); // managed entity, flushed at commit
    }

    /** CUSTOMERs may delete their own incident while it is OPEN; AGENT/ADMIN may delete any. */
    @Override
    @Transactional
    public void deleteIncident(Long id) {
        Incident incident = findIncident(id);
        enforceOwnership(incident);

        if (currentUserIsCustomerOnly() && incident.getStatus() != IncidentStatus.OPEN) {
            throw new BusinessException("Customers can only delete incidents while they are OPEN");
        }

        incidentRepository.delete(incident);
    }

    /**
     * Dedicated status endpoint (AGENT/ADMIN only). Enforces a small state machine
     * so incidents cannot jump to nonsensical states (e.g. CLOSED -> OPEN).
     */
    @Override
    @Transactional
    public IncidentResponse updateIncidentStatus(Long id, IncidentStatusUpdateRequest request) {
        Incident incident = findIncident(id);
        enforceOwnership(incident);

        if (!currentUserHasAnyRole(Role.AGENT, Role.ADMIN)) {
            // 403: authenticated, but this role is not allowed to drive the lifecycle
            throw new AccessDeniedException("Only AGENT or ADMIN can change incident status");
        }

        validateTransition(incident.getStatus(), request.status());

        incident.changeStatus(request.status());
        if (request.resolution() != null && !request.resolution().isBlank()) {
            if (request.status() != IncidentStatus.RESOLVED && request.status() != IncidentStatus.CLOSED) {
                throw new BusinessException("Resolution can only be set when status is RESOLVED or CLOSED");
            }
            incident.setResolution(request.resolution());
        }

        return IncidentResponse.fromEntity(incident);
    }

    // ---------- helpers ----------

    /** Allowed lifecycle transitions; CLOSED is terminal. */
    private void validateTransition(IncidentStatus current, IncidentStatus target) {
        boolean allowed = switch (current) {
            case OPEN -> target != IncidentStatus.OPEN;
            case IN_PROGRESS, WAITING_FOR_CUSTOMER -> target == IncidentStatus.RESOLVED
                    || target == IncidentStatus.CLOSED
                    || target == IncidentStatus.IN_PROGRESS
                    || target == IncidentStatus.WAITING_FOR_CUSTOMER;
            case RESOLVED -> target == IncidentStatus.CLOSED;
            case CLOSED -> false;
        };
        if (!allowed) {
            throw new BusinessException("Illegal status transition: %s -> %s".formatted(current, target));
        }
    }

    private Incident findIncident(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Incident", id));
    }

    /** The core ownership check: a CUSTOMER may only touch their own incidents -> 403 otherwise. */
    private void enforceOwnership(Incident incident) {
        CustomUserDetails currentUser = SecurityUtils.getCurrentUser();
        if (currentUser.hasRole(Role.CUSTOMER)
                && !incident.getReportedBy().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have access to this incident");
        }
    }

    private boolean currentUserIsCustomerOnly() {
        return SecurityUtils.getCurrentUser().hasRole(Role.CUSTOMER);
    }

    private boolean currentUserHasAnyRole(Role... roles) {
        CustomUserDetails currentUser = SecurityUtils.getCurrentUser();
        for (Role role : roles) {
            if (currentUser.hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pagination guard rails: cap page size and allow only whitelisted sort fields.
     * Prevents "size=100000" abuse and unsafe dynamic sorting.
     */
    private Pageable sanitize(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        Sort sort = sanitizeSort(pageable.getSort());
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }

    private Sort sanitizeSort(Sort sort) {
        List<Sort.Order> safeOrders = new ArrayList<>();
        for (Sort.Order order : sort) {
            String mapped = SORTABLE_FIELDS.get(order.getProperty());
            if (mapped != null) {
                safeOrders.add(new Sort.Order(order.getDirection(), mapped));
            }
        }
        if (safeOrders.isEmpty()) {
            safeOrders.add(Sort.Order.desc("createdAt")); // default: newest first
        }
        return Sort.by(safeOrders);
    }
}
