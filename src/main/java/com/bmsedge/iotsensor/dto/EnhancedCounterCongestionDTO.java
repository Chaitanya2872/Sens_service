package com.bmsedge.iotsensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedCounterCongestionDTO {
    private String timestamp;                    // Time bucket (e.g., "14:00")
    private Map<String, QueueStats> counterStats; // Statistics per counter
}