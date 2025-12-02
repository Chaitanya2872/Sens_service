package com.bmsedge.iotsensor.model;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String deviceId;
    private String type;              // IAQ / ODOR / ENERGY / PEOPLE_COUNT / TEMPERATURE / HUMIDITY / CO2 / PM25 / PM10 / TVOC / VOLTAGE / CURRENT / POWER_FACTOR / KWH / QUEUE_LENGTH
    private Double value;             // Sensor reading value
    private String unit;              // Unit of measurement (°C, ppm, µg/m³, etc.)
    private String status;            // NORMAL / WARNING / CRITICAL / OFFLINE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    private LocalDateTime timestamp;

    // Additional metadata
    private String quality;           // For IAQ: excellent, good, moderate, poor, unhealthy
    private Double threshold;         // Alert threshold value
    private String metadata;          // JSON string for additional data

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (status == null) {
            status = "NORMAL";
        }
    }
}