package com.bmsedge.iotsensor.model;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;             // Alert title
    private String message;           // Detailed message
    private String severity;          // LOW / MEDIUM / HIGH / CRITICAL
    private String type;              // IAQ / ENERGY / CAFETERIA / RESTROOM
    private String status;            // ACTIVE / ACKNOWLEDGED / RESOLVED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_data_id")
    private SensorData sensorData;

    private LocalDateTime createdAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;
    private String acknowledgedBy;
    private String resolvedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "ACTIVE";
        }
    }
}