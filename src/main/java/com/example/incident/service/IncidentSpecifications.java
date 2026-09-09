package com.example.incident.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.example.incident.entity.Incident;
import com.example.incident.entity.IncidentCategory;
import com.example.incident.entity.IncidentSeverity;
import com.example.incident.entity.IncidentStatus;

import jakarta.persistence.criteria.Predicate;

/**
 * Reusable query fragments (the "Specification" pattern).
 *
 * Instead of writing one repository method per filter combination
 * (findByStatusAndSeverity, findByStatusAndCategory, ... which explodes
 * combinatorially), we compose small Specifications and pass them to
 * JpaSpecificationExecutor.findAll(spec, pageable).
 *
 * A Specification<Incident> is essentially "a WHERE clause as an object".
 */
public final class IncidentSpecifications {

    private IncidentSpecifications() {
        // static utility class - no instances
    }

    /**
     * Builds the combined filter for GET /api/incidents.
     * Null parameters are simply ignored (no condition added).
     */
    public static Specification<Incident> withFilters(Long reporterId,
                                                      IncidentStatus status,
                                                      IncidentSeverity severity,
                                                      IncidentCategory category) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (reporterId != null) {
                // incident.reportedBy.id = :reporterId
                predicates.add(criteriaBuilder.equal(root.get("reportedBy").get("id"), reporterId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (severity != null) {
                predicates.add(criteriaBuilder.equal(root.get("severity"), severity));
            }
            if (category != null) {
                predicates.add(criteriaBuilder.equal(root.get("category"), category));
            }

            // AND-combined: every provided filter must match
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
