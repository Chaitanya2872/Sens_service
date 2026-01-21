package com.bmsedge.iotsensor.model;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "tenant")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String tenantCode;           // e.g., "intel-rmz-ecoworld"

    @Column(nullable = false, length = 200)
    private String tenantName;           // e.g., "Intel, RMZ Ecoworld, Bangalore"

    @Column(length = 100)
    private String organizationName;     // e.g., "Intel Corporation"

    @Column(length = 100)
    private String building;             // e.g., "RMZ Ecoworld"

    @Column(length = 100)
    private String city;                 // e.g., "Bangalore"

    @Column(length = 100)
    private String country;              // e.g., "India"

    private String description;
    private Boolean active;

    // Contact information
    @Column(length = 100)
    private String contactEmail;

    @Column(length = 50)
    private String contactPhone;

    @Column(length = 100)
    private String contactPerson;

    // Configuration
    @Column(columnDefinition = "TEXT")
    private String configJson;           // JSON string for tenant-specific configurations

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL)
    private List<CafeteriaLocation> cafeteriaLocations;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}