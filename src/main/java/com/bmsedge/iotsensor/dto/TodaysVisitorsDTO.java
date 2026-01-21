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
public class TodaysVisitorsDTO {
    private Integer total;
    private String sinceTime;
    private Integer lastHour;
    private Double percentageChange;
    private String trend;
}
