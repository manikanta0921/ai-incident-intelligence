package com.example.incident.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.example.incident.entity.Incident;
import com.example.incident.entity.IncidentCategory;
import com.example.incident.entity.IncidentSeverity;
import com.example.incident.entity.IncidentStatus;

/**
 * Database access for incidents.
 *
 * - JpaRepository gives us CRUD + pagination + sorting for free.
 * - JpaSpecificationExecutor powers the optional filters (status/severity/category)
 *   without requiring one repository method per filter combination.
 */
@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    // Spring Data JPA derives the SQL from these method names (query derivation).

    /** Used by the customer "my incidents" view. */
    List<Incident> findByReportedById(Long userId);

    /**
     * Ownership check for GET/PUT/PATCH: returns true only if the given user
     * reported the given incident. Loaded lazily by the service when needed.
     */
    boolean existsByIdAndReportedById(Long id, Long userId);

    /** Derived filters; Specifications are used when filters are combined dynamically. */
    List<Incident> findByStatus(IncidentStatus status);
    List<Incident> findBySeverity(IncidentSeverity severity);
    List<Incident> findByCategory(IncidentCategory category);
}
