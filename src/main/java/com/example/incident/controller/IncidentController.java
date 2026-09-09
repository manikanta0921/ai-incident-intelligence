package com.example.incident.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.incident.dto.common.PagedResponse;
import com.example.incident.dto.incident.IncidentRequest;
import com.example.incident.dto.incident.IncidentResponse;
import com.example.incident.dto.incident.IncidentStatusUpdateRequest;
import com.example.incident.dto.incident.IncidentUpdateRequest;
import com.example.incident.entity.IncidentCategory;
import com.example.incident.entity.IncidentSeverity;
import com.example.incident.entity.IncidentStatus;
import com.example.incident.security.CustomUserDetails;
import com.example.incident.service.IncidentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Incident REST endpoints. This class deliberately contains no business logic:
 * it validates input, delegates to IncidentService and wraps results in HTTP.
 */
@RestController
@RequestMapping("/api/incidents")
@Tag(name = "Incidents", description = "Create, list, inspect, update and resolve incidents")
@SecurityRequirement(name = "bearerAuth") // all endpoints here require a JWT
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // 201: a new resource was created
    @Operation(summary = "Create an incident", description = "The incident is owned by the authenticated CUSTOMER")
    public IncidentResponse create(@Valid @RequestBody IncidentRequest request,
                                   @AuthenticationPrincipal CustomUserDetails currentUser) {
        return incidentService.createIncident(request, currentUser);
    }

    @GetMapping
    @Operation(summary = "List incidents (paginated + filtered)",
            description = "CUSTOMERs always see only their own incidents; AGENT/ADMIN see all")
    public PagedResponse<IncidentResponse> getAll(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            @Parameter(hidden = true) Pageable pageable,

            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentSeverity severity,
            @RequestParam(required = false) IncidentCategory category,
            @RequestParam(required = false) Long reporterId) {
        return PagedResponse.of(
                incidentService.getAllIncidents(reporterId, status, severity, category, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one incident by id",
            description = "CUSTOMERs can only read their own incidents (ownership enforced server-side)")
    public ResponseEntity<IncidentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.getIncidentById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an incident",
            description = "CUSTOMERs can edit their own incident only while it is OPEN")
    public ResponseEntity<IncidentResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody IncidentUpdateRequest request) {
        return ResponseEntity.ok(incidentService.updateIncident(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change incident status (AGENT/ADMIN)",
            description = "Enforces the status state machine; optionally sets the resolution text")
    public ResponseEntity<IncidentResponse> updateStatus(@PathVariable Long id,
                                                         @Valid @RequestBody IncidentStatusUpdateRequest request) {
        return ResponseEntity.ok(incidentService.updateIncidentStatus(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT) // 204: nothing left to return after deletion
    @Operation(summary = "Delete an incident",
            description = "CUSTOMERs can delete their own OPEN incidents; ADMIN can delete any")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        incidentService.deleteIncident(id);
        return ResponseEntity.noContent().build();
    }
}
