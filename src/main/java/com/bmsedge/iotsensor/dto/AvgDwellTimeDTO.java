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
public class AvgDwellTimeDTO {
    private Integer minutes;
    private Integer seconds;
    private Integer totalSeconds;
    private String formatted;
    private Double percentageChange;
    private String trend;
    private String note;
}