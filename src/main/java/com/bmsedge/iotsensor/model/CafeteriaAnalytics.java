package com.bmsedge.iotsensor.model;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "cafeteria_analytics", indexes = {
        @Index(name = "idx_timestamp", columnList = "timestamp"),
        @Index(name = "idx_cafeteria_timestamp", columnList = "cafeteria_location_id,timestamp"),
        @Index(name = "idx_counter_timestamp", columnList = "food_counter_id,timestamp")
})
public class CafeteriaAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cafeteria_location_id", nullable = false)
    private CafeteriaLocation cafeteriaLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_counter_id")
    private FoodCounter foodCounter;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    // Occupancy Data
    private Integer currentOccupancy;    // Current number of people
    private Integer capacity;            // Total capacity
    private Double occupancyPercentage;  // Calculated percentage

    // Flow Data (from msg.payload)
    private Integer inCount;             // People entering
    private Integer outCount;            // People leaving (calculated)
    private Integer netFlow;             // Net flow (in - out)

    // Dwell Time Data (from msg.payload)
    private Double avgDwellTime;         // region_1_avg_dwell (in seconds)
    private Double maxDwellTime;         // region_1_max_dwell (in seconds)

    // Wait Time Data (from msg.payload)
    private Double estimatedWaitTime;    // estimate_wait_time (in minutes)
    private Double manualWaitTime;       // waiting_time_min (manual entry)

    // Queue Data
    private Integer queueLength;         // Number of people in queue

    // Status Indicators
    @Column(length = 50)
    private String congestionLevel;      // LOW, MEDIUM, HIGH

    @Column(length = 50)
    private String serviceStatus;        // READY_TO_SERVE, SHORT_WAIT, MEDIUM_WAIT, LONG_WAIT

    // Metadata
    @Column(columnDefinition = "TEXT")
    private String metadata;             // JSON for additional data

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();

        // Calculate occupancy percentage
        if (capacity != null && capacity > 0 && currentOccupancy != null) {
            occupancyPercentage = (currentOccupancy.doubleValue() / capacity) * 100;
        }

        // Determine congestion level
        if (occupancyPercentage != null) {
            if (occupancyPercentage < 40) {
                congestionLevel = "LOW";
            } else if (occupancyPercentage < 75) {
                congestionLevel = "MEDIUM";
            } else {
                congestionLevel = "HIGH";
            }
        }

        // Determine service status based on queue length
        if (queueLength != null) {
            if (queueLength < 8) {
                serviceStatus = "READY_TO_SERVE";
            } else if (queueLength < 15) {
                serviceStatus = "SHORT_WAIT";
            } else if (queueLength < 25) {
                serviceStatus = "MEDIUM_WAIT";
            } else {
                serviceStatus = "LONG_WAIT";
            }
        }
    }
}