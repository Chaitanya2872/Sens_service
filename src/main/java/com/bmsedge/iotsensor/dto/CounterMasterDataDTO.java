package com.bmsedge.iotsensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for Counter Master Data Listing
 * Represents a single row in the master data report
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterMasterDataDTO {

    // Counter Information
    private Long counterId;
    private String counterName;
    private String counterCode;
    private String counterType;
    private String deviceId;

    // Location Information
    private String cafeteriaName;
    private String cafeteriaCode;
    private String location;

    // Analytics Data
    private LocalDateTime timestamp;
    private Integer currentOccupancy;
    private Integer capacity;
    private Double occupancyPercentage;

    // Wait Time Information
    private Double avgDwellTime;
    private Double estimatedWaitTime;
    private Double manualWaitTime;
    private Integer queueLength;

    // Status Information
    private String congestionLevel;      // LOW, MEDIUM, HIGH
    private String serviceStatus;        // READY_TO_SERVE, SHORT_WAIT, MEDIUM_WAIT, LONG_WAIT

    // Additional Metrics
    private Integer inCount;
    private Double maxDwellTime;

    // Computed Fields
    private String waitTimeDisplay;      // "5.2 min" or "N/A"
    private String statusDisplay;        // Friendly status text
    private Boolean isActive;
}