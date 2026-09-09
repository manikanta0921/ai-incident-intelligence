package com.example.incident.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Incident/ticket aggregate root.
 *
 * NOTE: this entity is never returned directly from controllers; the API layer
 * works with IncidentRequest/IncidentResponse DTOs only.
 */
@Entity
@Table(
        name = "incidents",
        indexes = {
                // Filtering by status (GET /api/incidents?status=OPEN) is the most common query,
                // so an index keeps it fast as the table grows.
                @Index(name = "idx_incident_status", columnList = "status"),
                // Severity drives triage queues ("show me all CRITICAL incidents first").
                @Index(name = "idx_incident_severity", columnList = "severity"),
                // Category is used for dashboards and, in later phases, similarity grouping.
                @Index(name = "idx_incident_category", columnList = "category")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA needs a no-arg constructor, but nothing else should use it
@AllArgsConstructor // used by @Builder
@Builder
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Short human-readable summary. */
    @Column(nullable = false, length = 200)
    private String title;

    /** Full description of what went wrong. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING) // store readable enum names, not fragile ordinals
    @Column(nullable = false, length = 30)
    private IncidentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentSeverity severity;

    /** AI-generated summary (populated in a later phase; nullable until then). */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** Resolution text (populated by agents). */
    @Column(columnDefinition = "TEXT")
    private String resolution;

    /** The customer who reported this incident. Set by the service from the authenticated principal. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_by_id", nullable = false, foreignKey = @ForeignKey(name = "fk_incident_reported_by"))
    private User reportedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    /** Public status transitions; the service validates business rules on top of this. */
    public void changeStatus(IncidentStatus newStatus) {
        this.status = newStatus;
    }
}
