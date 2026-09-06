package com.example.incident.entity;

/**
 * Functional area the incident belongs to.
 * This mapping will later be produced automatically by the AI classifier.
 */
public enum IncidentCategory {
    PAYMENT,
    LOGIN,
    ORDER,
    DELIVERY,
    ACCOUNT,
    TECHNICAL,
    OTHER
}
