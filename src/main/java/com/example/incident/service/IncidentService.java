package com.example.incident.service;

import com.example.incident.dto.incident.IncidentRequest;
import com.example.incident.dto.incident.IncidentResponse;
import com.example.incident.dto.incident.IncidentStatusUpdateRequest;
import com.example.incident.dto.incident.IncidentUpdateRequest;
import com.example.incident.entity.IncidentCategory;
import com.example.incident.entity.IncidentSeverity;
import com.example.incident.entity.IncidentStatus;
import com.example.incident.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Incident business logic contract. The controller depends on this interface,
 * not on the implementation - easier to mock in tests and to swap later.
 */
public interface IncidentService {

    /** Creates a new incident owned by the given user. Status starts at OPEN. */
    IncidentResponse createIncident(IncidentRequest request, CustomUserDetails currentUser);

    /** Paginated + filtered listing; CUSTOMERs always see only their own incidents. */
    Page<IncidentResponse> getAllIncidents(Long reporterId, IncidentStatus status,
                                           IncidentSeverity severity, IncidentCategory category,
                                           Pageable pageable);

    IncidentResponse getIncidentById(Long id);

    IncidentResponse updateIncident(Long id, IncidentUpdateRequest request);

    void deleteIncident(Long id);

    /** AGENT/ADMIN only; enforces the status state machine. */
    IncidentResponse updateIncidentStatus(Long id, IncidentStatusUpdateRequest request);
}
