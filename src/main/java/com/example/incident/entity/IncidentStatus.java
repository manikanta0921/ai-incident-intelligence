package com.example.incident.entity;

/**
 * Lifecycle status of an incident.
 *
 * OPEN                 -> newly created, not yet picked up
 * IN_PROGRESS          -> an agent is working on it
 * WAITING_FOR_CUSTOMER -> agent needs more info from the reporter
 * RESOLVED             -> fix provided, waiting for confirmation
 * CLOSED               -> final state, no further changes expected
 */
public enum IncidentStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_FOR_CUSTOMER,
    RESOLVED,
    CLOSED
}
