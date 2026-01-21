package com.bmsedge.iotsensor.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterStatusDTO
{
    private String counterName;
    private Integer queueLength;
    private Double waitTime;
    private String congestionLevel;
    private String serviceStatus;
    private LocalDateTime lastUpdated;
}
