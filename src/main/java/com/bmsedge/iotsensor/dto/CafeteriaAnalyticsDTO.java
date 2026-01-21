package com.bmsedge.iotsensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CafeteriaAnalyticsDTO {
    private Long id;
    private String tenantCode;
    private String tenantName;
    private String cafeteriaCode;
    private String cafeteriaName;
    private String counterCode;
    private String counterName;
    private LocalDateTime timestamp;

    // Occupancy
    private Integer currentOccupancy;
    private Integer capacity;
    private Double occupancyPercentage;

    // Flow
    private Integer inCount;
    private Integer outCount;
    private Integer netFlow;

    // Dwell Time
    private Double avgDwellTime;
    private Double maxDwellTime;

    // Wait Time
    private Double estimatedWaitTime;
    private Double manualWaitTime;

    // Queue
    private Integer queueLength;

    // Status
    private String congestionLevel;
    private String serviceStatus;
}