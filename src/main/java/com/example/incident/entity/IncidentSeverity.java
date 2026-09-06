package com.example.incident.entity;

/**
 * Business impact of the incident.
 * Later phases will use AI to suggest a severity based on the description.
 */
public enum IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
