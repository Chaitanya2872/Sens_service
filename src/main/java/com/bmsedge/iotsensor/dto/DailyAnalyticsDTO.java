package com.bmsedge.iotsensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyAnalyticsDTO {
    private String date;
    private String counterName;
    private Double avgOccupancy;
    private Integer maxOccupancy;
    private Double avgQueue;
    private Double avgDwell;
    private Integer totalInflow;
    private Long recordCount;
}