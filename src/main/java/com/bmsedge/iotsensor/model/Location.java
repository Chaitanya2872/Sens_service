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
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;              // e.g., "Floor 3 - Zone A"
    private String type;              // CAFETERIA / IAQ / RESTROOM / ENERGY
    private Integer floor;            // Floor number
    private String zone;              // Zone identifier (A, B, C, etc.)
    private String building;          // Building name/identifier
    private String description;       // Additional details

    // GPS coordinates (optional)
    private Double latitude;
    private Double longitude;

    private Boolean active;           // Is location active
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "location", cascade = CascadeType.ALL)
    private List<SensorData> sensorData;

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