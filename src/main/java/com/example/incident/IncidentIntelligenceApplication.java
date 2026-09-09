package com.example.incident;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.incident.config.CorsProperties;
import com.example.incident.security.JwtProperties;

/**
 * Entry point of the AI Incident Intelligence backend.
 *
 * Later phases will add AI classification, similarity search and resolution
 * recommendation on top of this incident-management foundation.
 */
@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
@EnableScheduling // powers the refresh-token cleanup job
public class IncidentIntelligenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentIntelligenceApplication.class, args);
    }
}
