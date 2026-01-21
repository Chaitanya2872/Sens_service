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
public class HourlyAnalyticsDTO {
    private Integer hour;
    private String counterName;
    private Double avgOccupancy;
    private Integer maxOccupancy;
    private Double avgQueue;
    private Integer maxQueue;
    private Double avgDwell;
    private Double avgWait;
    private Integer totalInflow;
    private Long recordCount;
}