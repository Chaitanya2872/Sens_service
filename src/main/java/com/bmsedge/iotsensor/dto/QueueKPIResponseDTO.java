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
public class QueueKPIResponseDTO {
    private Double overallAvgQueue;
    private Double peakQueueLength;
    private String mostCongestedCounter;
    private Double congestionRate;
    private Double peakHourAvgQueue;
    private String peakHourRange;
    private String timeRange;
    private LocalDateTime reportGeneratedAt;
}