package com.bmsedge.iotsensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OccupancyStatusDTO {
    private Integer currentOccupancy;
    private Integer capacity;
    private Double occupancyPercentage;
    private String congestionLevel;
    private LocalDateTime timestamp;
}