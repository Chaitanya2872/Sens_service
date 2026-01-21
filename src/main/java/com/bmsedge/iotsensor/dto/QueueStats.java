package com.bmsedge.iotsensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueStats {
    private Integer maxQueue;      // Peak queue length in this time bucket
    private Double avgQueue;       // Average queue length
    private Integer minQueue;      // Minimum queue length
    private Integer dataPoints;    // Number of measurements
    private String status;
    // LIGHT, MODERATE, HEAVY based on maxQueue
}